package buildcraft.transport.stripes;

import buildcraft.BuildCraftTransport;
import buildcraft.api.core.Position;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.core.proxy.CoreProxy;
import buildcraft.transport.Pipe;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TileGenericPipe;
import buildcraft.transport.TravelingItem;
import buildcraft.transport.utils.TransportUtils;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;

public class PipeExtensionListener
{
	private class PipeExtensionRequest
	{
		public ItemStack stack;
		public int x, y, z;
		public ForgeDirection o;
		public IStripesActivator h;
	}

	// TODO gamerforEA code replace, old code:
	// private final Map<World, HashSet<PipeExtensionRequest>> requests = new HashMap<World, HashSet<PipeExtensionRequest>>();
	private final Map<World, HashSet<PipeExtensionRequest>> requests = new WeakHashMap<>();
	// TODO gamerforEA code end

	public void requestPipeExtension(ItemStack stack, World world, int x, int y, int z, ForgeDirection o, IStripesActivator h)
	{
		if (world.isRemote)
			return;

		HashSet<PipeExtensionRequest> rSet = this.requests.computeIfAbsent(world, k -> new HashSet<>());
		PipeExtensionRequest r = new PipeExtensionRequest();
		r.stack = stack;
		r.x = x;
		r.y = y;
		r.z = z;
		r.o = o;
		r.h = h;
		rSet.add(r);
	}

	@SubscribeEvent
	public void tick(TickEvent.WorldTickEvent event)
	{
		if (event.phase == TickEvent.Phase.END)
		{
			World world = event.world;
			HashSet<PipeExtensionRequest> rSet = this.requests.get(world);
			if (rSet == null)
				return;

			for (PipeExtensionRequest r : rSet)
			{
				Position target = new Position(r.x, r.y, r.z);
				target.orientation = r.o;

				boolean retract = r.stack.getItem() == BuildCraftTransport.pipeItemsVoid;
				ArrayList<ItemStack> removedPipeStacks = null;

				if (retract)
				{
					target.moveBackwards(1.0D);
					if (world.getBlock((int) target.x, (int) target.y, (int) target.z) != BuildCraftTransport.genericPipeBlock)
					{
						r.h.sendItem(r.stack, r.o.getOpposite());
						continue;
					}

					target.moveBackwards(1.0D);
					if (world.getBlock((int) target.x, (int) target.y, (int) target.z) != BuildCraftTransport.genericPipeBlock)
					{
						r.h.sendItem(r.stack, r.o.getOpposite());
						continue;
					}

					target.moveForwards(1.0D);
				}
				else
				{
					target.moveForwards(1.0D);
					if (!world.isAirBlock((int) target.x, (int) target.y, (int) target.z))
					{
						r.h.sendItem(r.stack, r.o.getOpposite());
						continue;
					}
				}

				// Step	1: Copy over and remove existing pipe
				Block oldBlock = world.getBlock(r.x, r.y, r.z);

				// TODO gamerforEA code start
				if (oldBlock != BuildCraftTransport.genericPipeBlock)
				{
					r.h.sendItem(r.stack, r.o.getOpposite());
					continue;
				}
				// TODO gamerforEA code end

				int oldMeta = world.getBlockMetadata(r.x, r.y, r.z);
				NBTTagCompound nbt = new NBTTagCompound();
				TileEntity oldTile = world.getTileEntity(r.x, r.y, r.z);

				// TODO gamerforEA code start
				if (!(oldTile instanceof TileGenericPipe))
				{
					r.h.sendItem(r.stack, r.o.getOpposite());
					continue;
				}
				// TODO gamerforEA code end

				oldTile.writeToNBT(nbt);
				world.setBlockToAir(r.x, r.y, r.z);

				boolean failedPlacement = false;

				// Step 2: If retracting, remove previous pipe; if extending, add new pipe
				if (retract)
				{
					removedPipeStacks = world.getBlock((int) target.x, (int) target.y, (int) target.z).getDrops(world, (int) target.x, (int) target.y, (int) target.z, world.getBlockMetadata((int) target.x, (int) target.y, (int) target.z), 0);

					world.setBlockToAir((int) target.x, (int) target.y, (int) target.z);
				}
				else if (!r.stack.getItem().onItemUse(r.stack, CoreProxy.proxy.getBuildCraftPlayer((WorldServer) world, r.x, r.y, r.z).get(), world, r.x, r.y, r.z, 1, 0, 0, 0))
				{
					failedPlacement = true;
					target.moveBackwards(1.0D);
				}

				// Step 3: Place stripes pipe back
				// - Correct NBT coordinates
				nbt.setInteger("x", (int) target.x);
				nbt.setInteger("y", (int) target.y);
				nbt.setInteger("z", (int) target.z);
				// - Create block and tile
				TileGenericPipe pipeTile = (TileGenericPipe) TileEntity.createAndLoadEntity(nbt);

				world.setBlock((int) target.x, (int) target.y, (int) target.z, oldBlock, oldMeta, 3);
				world.setTileEntity((int) target.x, (int) target.y, (int) target.z, pipeTile);

				pipeTile.setWorldObj(world);
				pipeTile.validate();
				pipeTile.updateEntity();

				// Step 4: Hope for the best, clean up.
				PipeTransportItems items = (PipeTransportItems) pipeTile.pipe.transport;
				if (!retract && !failedPlacement)
					r.stack.stackSize--;

				if (r.stack.stackSize > 0)
					this.sendItem(items, r.stack, r.o.getOpposite());
				if (removedPipeStacks != null)
					for (ItemStack s : removedPipeStacks)
					{
						this.sendItem(items, s, r.o.getOpposite());
					}

				if (!retract && !failedPlacement)
				{
					TileGenericPipe newPipeTile = (TileGenericPipe) world.getTileEntity(r.x, r.y, r.z);
					newPipeTile.updateEntity();
					pipeTile.scheduleNeighborChange();
					if (pipeTile.getPipe() != null)
						((Pipe) pipeTile.getPipe()).scheduleWireUpdate();
				}
			}
			rSet.clear();
		}
	}

	private void sendItem(PipeTransportItems transport, ItemStack itemStack, ForgeDirection direction)
	{
		TravelingItem newItem = TravelingItem.make(transport.container.xCoord + 0.5, transport.container.yCoord + TransportUtils.getPipeFloorOf(itemStack), transport.container.zCoord + 0.5, itemStack);
		transport.injectItem(newItem, direction);
	}
}
