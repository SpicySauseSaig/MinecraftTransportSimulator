package minecrafttransportsimulator.rendering.components;

import org.lwjgl.opengl.GL11;

import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.entities.components.AEntityC_Definable;
import minecrafttransportsimulator.entities.components.AEntityD_Interactable;
import minecrafttransportsimulator.entities.instances.APart;
import minecrafttransportsimulator.entities.instances.EntityPlayerGun;
import minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics;
import minecrafttransportsimulator.entities.instances.PartSeat;
import minecrafttransportsimulator.jsondefs.JSONAnimationDefinition;
import minecrafttransportsimulator.jsondefs.JSONAnimationDefinition.AnimationComponentType;
import minecrafttransportsimulator.jsondefs.JSONCameraObject;
import minecrafttransportsimulator.mcinterface.InterfaceClient;
import minecrafttransportsimulator.mcinterface.WrapperPlayer;
import minecrafttransportsimulator.mcinterface.WrapperWorld;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**Interface for handling events pertaining to camera orientation.  This class is responsible for handling
 * camera position and orientation operations.  The only camera functions this class does not handle is overlays,
 * as those are 2D rendering of textures, not 3D orientation of the camera itself. 
 *
 * @author don_bruce
 */
@EventBusSubscriber(Side.CLIENT)
public class InterfaceEventsCamera{
    private static int zoomLevel;
	private static boolean enableCustomCameras;
	private static boolean runningCustomCameras;
	private static int customCameraIndex;
	private static float currentFOV;
	public static String customCameraOverlay;
	
	/**
	 *  Adjusts the camera zoom, zooming in or out depending on the flag.
	 */
	public static void changeCameraZoom(boolean zoomIn){
		if(zoomIn && zoomLevel > 0){
			zoomLevel -= 2;
		}else if(!zoomIn){
			zoomLevel += 2;
		}
	}
	
    /**
	 * Main event call for cameras.  This drives all camera logic.
	 * Adjusts roll, pitch, and zoom for camera.
     * Roll and pitch only gets updated when in first-person as we use OpenGL transforms.
     * For external rotations, we just let the entity adjust the player's pitch and yaw.
     * This is because first-person view is for direct control, while third-person is for passive control.
	 */
    @SubscribeEvent
    public static void on(CameraSetup event){
    	if(event.getEntity() instanceof EntityPlayer){
	    	//Get variables.
    		WrapperPlayer player = WrapperWorld.getWrapperFor(event.getEntity().world).getWrapperFor((EntityPlayer) event.getEntity());
			AEntityD_Interactable<?> ridingEntity = player.getEntityRiding();
			EntityVehicleF_Physics vehicle = ridingEntity instanceof EntityVehicleF_Physics ? (EntityVehicleF_Physics) ridingEntity : null;
			PartSeat sittingSeat = vehicle != null ? (PartSeat) vehicle.getPartAtLocation(vehicle.locationRiderMap.inverse().get(player)) : null;
			EntityPlayerGun playerGunEntity = EntityPlayerGun.playerClientGuns.get(player.getUUID());
			float partialTicks = (float) event.getRenderPartialTicks();
			
	    	if(InterfaceClient.inFirstPerson()){
	    		//If we are sneaking and holding a gun, enable custom cameras.
	    		if(playerGunEntity != null && playerGunEntity.activeGun != null && sittingSeat == null){
	    			enableCustomCameras = player.isSneaking() && !InterfaceRender.shadersDetected;
	    			customCameraIndex = 0;
	    		}
	    		
	    		//If our seat is set to automatically use custom cameras, enable them.
        		if(!enableCustomCameras && sittingSeat != null && sittingSeat.placementDefinition.forceCameras){
    				enableCustomCameras = true;
	    			customCameraIndex = 0;
        		}
	    		
				//Do custom camera, or do normal rendering.
				if(enableCustomCameras){
			    	//Get cameras from vehicle or hand-held gun.
			    	//We check active cameras until we find one that we can use.
					runningCustomCameras = true;
			    	int camerasChecked = 0;
			    	JSONCameraObject camera = null;
			    	AEntityC_Definable<?> cameraProvider = null;
			    	
					if(vehicle != null){
						if(vehicle.definition.rendering.cameraObjects != null){
							for(JSONCameraObject testCamera : vehicle.definition.rendering.cameraObjects){
								if(isCameraActive(testCamera, vehicle, partialTicks)){
									if(camerasChecked++ == customCameraIndex){
										camera = testCamera;
										cameraProvider = vehicle;
										break;
									}
								}
							}
						}
						for(APart part : vehicle.parts){
							if(part.definition.rendering != null && part.definition.rendering.cameraObjects != null){
								for(JSONCameraObject testCamera : part.definition.rendering.cameraObjects){
									if(isCameraActive(testCamera, part, partialTicks)){
										if(camerasChecked++ == customCameraIndex){
											camera = testCamera;
											cameraProvider = part;
											break;
										}
									}
								}
							}
							if(camera != null){
								break;
							}
						}
					}else if(playerGunEntity != null && playerGunEntity.activeGun != null){
						if(playerGunEntity.activeGun.definition.rendering != null && playerGunEntity.activeGun.definition.rendering.cameraObjects != null){
							for(JSONCameraObject testCamera : playerGunEntity.activeGun.definition.rendering.cameraObjects){
								if(isCameraActive(testCamera, playerGunEntity, partialTicks)){
									if(camerasChecked++ == customCameraIndex){
										camera = testCamera;
										cameraProvider = playerGunEntity;
										break;
									}
								}
							}
						}
					}
					
					//If we found a camera, use it.  If not, turn off custom cameras and go back to first-person mode.
					if(camera != null){
						//Set current overlay for future calls.
						customCameraOverlay = camera.overlay != null ? camera.overlay + ".png" : null;
	        			
						//Set variables for camera position and rotation.
						Point3d cameraPosition = new Point3d();
						Point3d cameraRotation = new Point3d();
						
						//Apply transforms.
						//These happen in-order to ensure proper rendering sequencing.
						if(camera.animations != null){
							boolean inhibitAnimations = false;
	        				for(JSONAnimationDefinition animation : camera.animations){
	        					double variableValue= cameraProvider.getAnimator().getAnimatedVariableValue(cameraProvider, animation, 0, null, partialTicks);
	        					switch(animation.animationType){
		        					case TRANSLATION :{
	            						if(!inhibitAnimations && variableValue != 0){
	            							Point3d translationAmount = animation.axis.copy().normalize().multiply(variableValue).rotateFine(cameraRotation);
	            							cameraPosition.add(translationAmount);
	            						}
	            						break;
	            					}
		        					case ROTATION :{
		        						if(!inhibitAnimations && variableValue != 0){
	            							Point3d rotationAmount = animation.axis.copy().normalize().multiply(variableValue);
	            							Point3d rotationOffset = camera.pos.copy().subtract(animation.centerPoint);
	            							if(!rotationOffset.isZero()){
	            								cameraPosition.subtract(rotationOffset).add(rotationOffset.rotateFine(rotationAmount));
	            							}
	            							cameraRotation.add(rotationAmount);
	            						}
		        						break;
		        					}
		        					case VISIBILITY :{
		        						//Do nothing.  We checked this earlier.
		        						break;
		        					}
		        					case INHIBITOR :{
		        						if(variableValue >= animation.clampMin && variableValue <= animation.clampMax){
		        							inhibitAnimations = true;
		        						}
		        						break;
		        					}
		        					case ACTIVATOR :{
		        						if(variableValue >= animation.clampMin && variableValue <= animation.clampMax){
		        							inhibitAnimations = false;
		        						}
		        						break;
		        					}
	        					}
	        				}
	        			}
						
	    				//Now that the transformed camera is ready, add the camera initial offset position and rotation.
						Point3d entityAnglesDelta = cameraProvider.angles.copy().subtract(cameraProvider.prevAngles).multiply(partialTicks).add(cameraProvider.prevAngles);
						cameraRotation.add(entityAnglesDelta);
						cameraPosition.add(camera.pos.copy().rotateFine(entityAnglesDelta));
	    				if(camera.rot != null){
	    					cameraRotation.add(camera.rot);
	    				}
	    				
	    				//Camera is positioned and rotated to match the entity.  Do OpenGL transforms to set it.
						//Get the distance from the entity's center point to the rendered player to get a 0,0,0 starting point.
	        			//Need to take into account the player's eye height.  This is where the camera is, but not where the player is positioned.
						Point3d entityPositionDelta = cameraProvider.position.copy().subtract(cameraProvider.prevPosition).multiply(partialTicks).add(cameraProvider.prevPosition);
						entityPositionDelta.subtract(player.getRenderedPosition(partialTicks).add(0, player.getEyeHeight(), 0));
						cameraPosition.add(entityPositionDelta);
						
						
						
						//Rotate by 180 to get the forwards-facing orientation; MC does everything backwards.
	            		GL11.glRotated(180, 0, 1, 0);
						
						//Now apply our actual offsets.
						GL11.glRotated(-cameraRotation.z, 0, 0, 1);
						GL11.glRotated(-cameraRotation.x, 1, 0, 0);
	        			GL11.glRotated(-cameraRotation.y, 0, 1, 0);
	        			GL11.glTranslated(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
	            		
	            		//If the camera has an FOV override, apply it.
	            		if(camera.fovOverride != 0){
	            			if(currentFOV == 0){
	            				currentFOV = InterfaceClient.getFOV();
	            			}
	            			InterfaceClient.setFOV(camera.fovOverride);
	            		}
	            		
	            		//Set camera variables to 0 as we overrode them and return.
	            		event.setPitch(0);
	    	    		event.setYaw(0);
	    	    		return;
					}
				}else if(sittingSeat != null){
	            	//Get yaw delta between entity and player from-180 to 180.
	            	double playerYawDelta = (360 + (vehicle.angles.y - player.getHeadYaw())%360)%360;
	            	if(playerYawDelta > 180){
	            		playerYawDelta-=360;
	            	}
	            	
	            	//Get the angles from -180 to 180 for use by the component system for calculating roll and pitch angles.
	            	double pitchAngle = vehicle.prevAngles.x + (vehicle.angles.x - vehicle.prevAngles.x)*partialTicks;
	            	double rollAngle = vehicle.prevAngles.z + (vehicle.angles.z - vehicle.prevAngles.z)*partialTicks;
	            	while(pitchAngle > 180){pitchAngle -= 360;}
	    			while(pitchAngle < -180){pitchAngle += 360;}
	    			while(rollAngle > 180){rollAngle -= 360;}
	    			while(rollAngle < -180){rollAngle += 360;}
	            	
	            	//Get the component of the pitch and roll that should be applied based on the yaw delta.
	            	//This is based on where the player is looking.  If the player is looking straight forwards, then we want 100% of the
	            	//pitch to be applied as pitch.  But, if they are looking to the side, then we need to apply that as roll, not pitch.
	            	double rollRollComponent = Math.cos(Math.toRadians(playerYawDelta))*rollAngle;
	            	double pitchRollComponent = -Math.sin(Math.toRadians(playerYawDelta))*pitchAngle;
	            	GL11.glRotated(rollRollComponent + pitchRollComponent, 0, 0, 1);
				}
				
				//We wern't running a custom camera.  Set running variable to false.
				enableCustomCameras = false;
				runningCustomCameras = false;
	    	}else if(InterfaceClient.inThirdPerson()){
	    		//If we were running a custom camera, and hit the switch key, increment our camera index.
	    		//We then go back to first-person to render the proper camera.
	    		//If we weren't running a custom camera, try running one.  This will become active when we
	    		//go back into first-person mode.  This only has an effect if we are in a vehicle.
	    		if(runningCustomCameras){
	    			++customCameraIndex;
	    			InterfaceClient.toggleFirstPerson();
	    		}else if(vehicle != null && !InterfaceRender.shadersDetected){
	    			enableCustomCameras = true;
	        		customCameraIndex = 0;
	    		}
	    		if(sittingSeat != null){
	    			GL11.glTranslated(-sittingSeat.localOffset.x, 0F, -zoomLevel);
	    		}
	        }else{
	        	//Assuming inverted third-person mode.
	        	//If we get here, and don't have any custom cameras, stay here.
	        	//If we do have custom cameras, use them instead.
	        	if(!InterfaceRender.shadersDetected){
		        	if(vehicle != null){
			        	if(vehicle.definition.rendering.cameraObjects != null){
			        		InterfaceClient.toggleFirstPerson();
						}else{
							for(APart part : vehicle.parts){
								if(part.definition.rendering != null && part.definition.rendering.cameraObjects != null){
									InterfaceClient.toggleFirstPerson();
									break;
								}
							}
						}
			        	if(sittingSeat != null){
			        		GL11.glTranslated(-sittingSeat.localOffset.x, 0F, zoomLevel);
			        	}
		        	}else if(playerGunEntity != null && playerGunEntity.activeGun != null && player.isSneaking()){
		        		if(playerGunEntity.activeGun.definition.rendering != null && playerGunEntity.activeGun.definition.rendering.cameraObjects != null){
		        			InterfaceClient.toggleFirstPerson();
		        		}
		        	}
	        	}
			}
			customCameraOverlay = null;
			if(currentFOV != 0){
				InterfaceClient.setFOV(currentFOV);
				currentFOV = 0; 
			}
    	}
    }
    
    private static boolean isCameraActive(JSONCameraObject camera, AEntityC_Definable<?> entity, float partialTicks){
		if(camera.animations != null){
			for(JSONAnimationDefinition animation : camera.animations){
				if(animation.animationType.equals(AnimationComponentType.VISIBILITY)){
					double value = entity.getAnimator().getAnimatedVariableValue(entity, animation, 0, null, partialTicks);
					if(value < animation.clampMin || value > animation.clampMax){
						return false;
					}
				}
			}
		}
		return true;
    }
}
