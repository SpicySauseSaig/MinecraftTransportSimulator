package minecrafttransportsimulator.packloading;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.jsondefs.AJSONItem;

/**This class contains various methods to parse out JSON data from JSON files.
 * Contains  custom type adapters used in JSON parsing operations, and annotations for fields.
 * Put here to customize how we import/export JSONs.  For importing, this is pretty much
 * un-changed, sans for checks on the null-ness of fields.  For exporting, this prevents writing 
 * default values to the JSON for clarity and changes how lists are written to make the JSONs more compact.
*
* @author don_bruce
*/
public class JSONParser{
	
	private static final TypeAdapter<Boolean> booleanAdapter = new TypeAdapter<Boolean>(){	
		@Override
		public Boolean read(JsonReader reader) throws IOException{
			if(reader.peek() == JsonToken.NULL){
				reader.nextNull();
				return null;
			}else{
				return reader.nextBoolean();
			}
		}
		
		@Override
		public void write(JsonWriter writer, Boolean value) throws IOException{
			//Only write booleans that are true to save space.
			if(!value){
				writer.nullValue();
			}else{
				writer.value(value);
			}
		}
	};
	
	private static final TypeAdapter<Integer> integerAdapter = new TypeAdapter<Integer>(){	
		@Override
		public Integer read(JsonReader reader) throws IOException{
			if(reader.peek() == JsonToken.NULL){
				reader.nextNull();
				return null;
			}else{
				return reader.nextInt();
			}
		}
		
		@Override
		public void write(JsonWriter writer, Integer value) throws IOException{
			//Only write non-zero integers to save space.
			if(value.intValue() == 0){
				writer.nullValue();
			}else{
				writer.value(value);
			}
		}
	};
	
	private static final TypeAdapter<Float> floatAdapter = new TypeAdapter<Float>(){	
		@Override
		public Float read(JsonReader reader) throws IOException{
			if(reader.peek() == JsonToken.NULL){
				reader.nextNull();
				return null;
			}else{
				return (float) reader.nextDouble();
			}
		}
		
		@Override
		public void write(JsonWriter writer, Float value) throws IOException{
			//Only write non-zero floats to save space.
			if(value.floatValue() == 0){
				writer.nullValue();
			}else{
				writer.value(value);
			}
		}
	};
	
	private static final TypeAdapter<Point3d> point3dAdapter = new TypeAdapter<Point3d>(){
		@Override
		public Point3d read(JsonReader reader) throws IOException{
			if(reader.peek() == JsonToken.NULL){
				reader.nextNull();
				return null;
			}else{
				reader.beginArray();
				Point3d value = new Point3d(reader.nextDouble(), reader.nextDouble(), reader.nextDouble());
				reader.endArray();
				return value;
			}
		}
		
		@Override
		public void write(JsonWriter writer, Point3d value) throws IOException{
			if(value == null){
				writer.nullValue();
				return;
			}else{
				//Setting the indent to nothing prevents GSON from applying newlines to Point3ds.
				//We need to set the indent to the value afterwards though to keep pretty printing.
				writer.beginArray();
				writer.setIndent("");
				writer.value(value.x);
				writer.value(value.y);
				writer.value(value.z);
				writer.endArray();
				writer.setIndent("  ");
			}
		}
	};
	
	private static final TypeAdapter<List<String>> stringListAdapter = new TypeAdapter<List<String>>(){	
		@Override
		public List<String> read(JsonReader reader) throws IOException{
			if(reader.peek() == JsonToken.NULL){
				reader.nextNull();
				return null;
			}else{
				List<String> value = new ArrayList<String>();
				reader.beginArray();
				while(reader.hasNext()){
					value.add(reader.nextString());
				}
				reader.endArray();
				return value;
			}
		}
		
		@Override
		public void write(JsonWriter writer, List<String> value) throws IOException{
			if(value == null){
				writer.nullValue();
			}else{
				//Setting the indent to nothing prevents GSON from applying newlines to String lists.
				//We need to set the indent to the value afterwards though to keep pretty printing.
				writer.beginArray();
				writer.setIndent("");
				for(String item : value){
					writer.value(item);
				}
				writer.endArray();
				writer.setIndent("  ");
			}
		}
	};
	
	//This needs to go down here AFTER we create the type adapters.
	private static final Gson packParser = getParserWithAdapters();
	
	private static Gson getParserWithAdapters(){
		return new GsonBuilder()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.registerTypeAdapter(Boolean.class, booleanAdapter)
				.registerTypeAdapter(Integer.class, integerAdapter)
				.registerTypeAdapter(Float.class, floatAdapter)
				.registerTypeAdapter(Point3d.class, point3dAdapter)
				.registerTypeAdapter(new TypeToken<List<String>>(){}.getType(), stringListAdapter)
				.create();
	}
	
	public static <JSONClass extends Object> JSONClass parseStream(InputStreamReader jsonReader, Class<JSONClass> retClass){
		JSONClass retObj = packParser.fromJson(jsonReader, retClass);
		//Do legacy compats if we need before validating the JSON.
		if(retObj instanceof AJSONItem){
			LegacyCompatSystem.performLegacyCompats((AJSONItem<?>) retObj);
		}
		//Check for proper fields.
		validateFields(retObj, "/");
		return retObj;
	}
	
	public static void exportStream(Object jsonObject, OutputStreamWriter jsonWriter){
		packParser.toJson(jsonObject, jsonObject.getClass(), jsonWriter);
	}
	
	@SuppressWarnings("unchecked")
	public static <JSONClass extends Object> JSONClass duplicateJSON(JSONClass objToDuplicate){
		return (JSONClass) packParser.fromJson(packParser.toJson(objToDuplicate), objToDuplicate.getClass());
	}
	
	@Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
	public static @interface JSONRequired{
		/**
         * Optional value for a linked field that is used to check if this field can be null.
         */
		String dependentField() default "";
		
		/**
         * Optional parameter for the optional variable value.  This field will only be marked as required if the 
         * {@link #dependentField()} has one of these values.  This allows for this field to be null as long as
         * the dependent doesn't have one of these values.  If this annotation element is not specified, then the
         * field must always be non-null.
         */
		String[] dependentValues() default {};
		
		/**
         * Optional subField for this linked field. Useful if a module is required in the main class if there's another
         * field with one of the dependentValues in a sub-class.
         */
		String subField() default "";
    }
	
	@Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
	public static @interface JSONDescription{
		/**
         * Description for this field.
         */
		String value() default "";
    }
	
	public static String checkRequiredState(Field field, Object objectOn, String pathPrefix){
		if(field.isAnnotationPresent(JSONRequired.class)){
			Object testObj = null;
			try{
				testObj = field.get(objectOn);
			}catch(Exception e){}
			
			if(testObj == null){
				JSONRequired annotation = field.getAnnotation(JSONRequired.class);
				//If we need another field, get it to check.
				String dependentVarName = annotation.dependentField();
				if(!dependentVarName.isEmpty()){
					Object depObj = null;
					try{
						if(annotation.subField().isEmpty()){
							depObj = objectOn.getClass().getField(dependentVarName).get(objectOn);
						}else{
							depObj = objectOn.getClass().getField(annotation.subField()).get(objectOn);
							depObj = depObj.getClass().getField(dependentVarName).get(depObj);
						}
					}catch(Exception e){e.printStackTrace();}
					
					if(depObj != null){
						//Have object.  If the object has to be a set of values to throw an error, check this.
						if(annotation.dependentValues().length == 0){
							return pathPrefix + field.getName() + " is required when '" + dependentVarName + "' is present!";
						}else{
							for(String possibleValue : annotation.dependentValues()){
								if(depObj.toString().startsWith(possibleValue)){
									return pathPrefix + field.getName() + " is required when value of '" + dependentVarName + "' is '" + depObj.toString() + "'!";
								}
							}
						}
					}
				}else{
					return pathPrefix + field.getName() + " is missing from the JSON and is required!";
				}
			}
		}
		return null;
	}
	
	private static void validateFields(Object obj, String priorObjects){
		//First get all fields that have the annotation with no values.
		for(Field field : obj.getClass().getFields()){
			String errorValue = checkRequiredState(field, obj, priorObjects);
			if(errorValue != null){
				throw new NullPointerException(errorValue);
			}
			
			//Check all fields of this field.
			//If we are a collection, validate each entry in ourselves rather than ourselves.
			//Only check for objects that are defined in the jsondefs class file.
			//If we extend recursion to others, it could get nasty...
			if(obj.getClass().getPackage().getName().contains("jsondefs")){
				Object recursiveObject = null;
				try{
					recursiveObject = field.get(obj);
				}catch(Exception e){}
				
				if(recursiveObject != null){
					if(recursiveObject instanceof Collection){
						int index = 0;
						for(Object objEntry : ((Collection<?>) recursiveObject)){
							if(objEntry != null){
								validateFields(objEntry, priorObjects + field.getName() + "/");
								++index;
							}else{
								throw new NullPointerException("Unable to parse item #" + index + " in variable set " + priorObjects + field.getName() + " due to it not existing.  Check your commas!");
							}
						}
					}else{
						validateFields(recursiveObject, priorObjects + field.getName() + "/");
					}
				}
			}
		}
	}
}
