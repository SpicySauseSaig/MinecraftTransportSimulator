package minecrafttransportsimulator.rendering.components;

import mcinterface.InterfaceRender;
import mcinterface.WrapperWorld;
import minecrafttransportsimulator.baseclasses.Point3d;

/**Class that is the base of all particles.
 * Particles may be spawned by calling {@link InterfaceRender#spawnParticle(AParticle)}
 *
 * @author don_bruce
 */
public abstract class AParticle{
	
	public final WrapperWorld world;
	public final Point3d position;
	public final Point3d motion;
	public final float red;
	public final float green;
	public final float blue;
	public final float alpha;
	public final float scale;
	public final int maxAge;
	public int age;
	
	public AParticle(WrapperWorld world, Point3d position, Point3d motion){
		this(world, position, motion, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
	}
	
	public AParticle(WrapperWorld world, Point3d position, Point3d motion, float red, float green, float blue, float alpha, float scale){
		this.world = world;
		this.position = position;
		this.motion = motion;
		this.red = red;
		this.green = green;
		this.blue = blue;
		this.alpha = alpha;
		this.scale = scale;
		this.maxAge = generateMaxAge();
	}
	
	/**
	 *  Called to update this particle's states.
	 */
	public void update(boolean onGround){
		position.add(motion);
		++age;
	}
	
	/**
	 *  Gets the current scale of the particle.
	 *  This may ore may not be the scale parameter.
	 */
	public abstract float getScale(float partialTicks);
	
	/**
	 *  Gets the current size of the particle.  This parameter
	 *  is used for the particle's bounding box, and does not need
	 *  to take the scale of the particle into account.
	 */
	public float getSize(){
		return 0.2F;
	}
	
	/**
	 *  Generates the max age for this particle.  This value is used only once on construction
	 *  to initialize the {@link #maxAge} parameter.  This parameter is how many ticks the particle will
	 *  render and move until it is removed from the world.
	 */
	protected abstract int generateMaxAge();
	
	/**
	 *  Returns the packed UV index for this texture.
	 *  The packed UV index is a single integer divided by 16, with the
	 *  quotient of the value being the U value, and the remainder
	 *  being the V value.  So for example, a texture index of 22
	 *  would have a quotient of 1, and a remainder of 6, so would have
	 *  a UV of 1,6.  If this particle does not have a texture index and
	 *  uses a custom model, return -1.
	 */
	public abstract int getTextureIndex();
	
	/**
	 *  Return true if this particle should be lit up at night.  This is normally
	 *  not the case, but may be if the particle needs to be bright.
	 */
	public boolean isBright(){
		return false;
	}
	
	/**
	 *  Renders this particle.  Normally this is automatic and handled by the 2D texture
	 *  rendering system based on the texture index returned by {@link #getTextureIndex()}.
	 *  However, if that function returns -1, this method will be called instead.
	 *  Note that you are required to bind the texture you will be using here in this case;
	 *  it will not be pre-bound for you.
	 */
	public void render(float partialTicks){}
}