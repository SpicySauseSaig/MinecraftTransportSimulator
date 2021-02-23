package minecrafttransportsimulator.packets.components;

import io.netty.buffer.ByteBuf;
import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import minecrafttransportsimulator.entities.components.AEntityA_Base;
import minecrafttransportsimulator.mcinterface.WrapperPlayer;
import minecrafttransportsimulator.mcinterface.WrapperWorld;

/**Packet class that includes a default implementation for transmitting an entity
 * to allow entity-specific interactions on the other side of the network.
 *
 * @author don_bruce
 */
public abstract class APacketEntity<EntityType extends AEntityA_Base> extends APacketBase{
	private final int lookupID;
	
	public APacketEntity(AEntityA_Base entity){
		super(null);
		this.lookupID = entity.lookupID;
	}
	
	public APacketEntity(ByteBuf buf){
		super(buf);
		this.lookupID = buf.readInt();
	};

	@Override
	public void writeToBuffer(ByteBuf buf){
		super.writeToBuffer(buf);
		buf.writeInt(lookupID);
	}
	
	@Override
	public void handle(WrapperWorld world, WrapperPlayer player){
		EntityType entity = AEntityA_Base.getEntity(world, lookupID);
		if(entity != null && handle(world, player, entity) && !world.isClient()){
			InterfacePacket.sendToAllClients(this);
			if(entity instanceof ATileEntityBase){
				//Need to set TEs as updated, as they don't normally do this.
				world.markTileEntityChanged(((ATileEntityBase<?>) entity).position);
			}
		}
	}
	
	/**
	 *  Helper method for handling clamped values.  Mainly comes from
	 *  control packets where we could go outside our desired bounds if we
	 *  don't check clamping.
	 */
	protected static int clampAngle(int min, int max, int value){
		return value < min ? min : (value > max ? max : value);
	}
	
	/**
	 *  Handler method with an extra parameter for the entity that this packet
	 *  is associated with. If the entity is null,  then this method won't be called.
	 *  Saves having to do null checks for every packet type.  If this is handled on the 
	 *  server, and a packet shouldn't be sent to all clients (like if the action failed due
	 *   to an issue) return false.  Otherwise, return true to send this packet on to all clients.  
	 *   Return method has no function on clients.
	 */
	protected abstract boolean handle(WrapperWorld world, WrapperPlayer player, EntityType entity);
}
