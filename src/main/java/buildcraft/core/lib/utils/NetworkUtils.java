package buildcraft.core.lib.utils;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class NetworkUtils
{
	private NetworkUtils()
	{

	}

	public static void writeUTF(ByteBuf data, String str)
	{
		if (str == null)
		{
			data.writeInt(0);
			return;
		}
		byte[] b = str.getBytes(StandardCharsets.UTF_8);
		data.writeInt(b.length);
		data.writeBytes(b);
	}

	public static String readUTF(ByteBuf data)
	{
		int len = data.readInt();
		if (len == 0)
			return "";
		byte[] b = new byte[len];
		data.readBytes(b);
		return new String(b, StandardCharsets.UTF_8);
	}

	public static void writeNBT(ByteBuf data, NBTTagCompound nbt)
	{
		try
		{
			byte[] compressed = CompressedStreamTools.compress(nbt);
			data.writeInt(compressed.length);
			data.writeBytes(compressed);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static NBTTagCompound readNBT(ByteBuf data)
	{
		try
		{
			int length = data.readInt();
			byte[] compressed = new byte[length];
			data.readBytes(compressed);
			return CompressedStreamTools.func_152457_a(compressed, NBTSizeTracker.field_152451_a);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	public static void writeStack(ByteBuf data, ItemStack stack)
	{
		if (stack == null || stack.getItem() == null || stack.stackSize < 0)
			data.writeByte(0);
		else
		{
			// ItemStacks generally shouldn't have a stackSize above 64,
			// so we use this "trick" to save bandwidth by storing it in the first byte.
			data.writeByte(MathUtils.clamp(stack.stackSize + 1, 0, 64) & 0x7F | (stack.hasTagCompound() ? 128 : 0));
			data.writeShort(Item.getIdFromItem(stack.getItem()));
			data.writeShort(stack.getItemDamage());
			if (stack.hasTagCompound())
				writeNBT(data, stack.getTagCompound());
		}
	}

	public static ItemStack readStack(ByteBuf data)
	{
		int flags = data.readUnsignedByte();
		if (flags == 0)
			return null;
		boolean hasCompound = (flags & 0x80) != 0;
		int stackSize = (flags & 0x7F) - 1;
		int itemId = data.readUnsignedShort();
		int itemDamage = data.readShort();

		/* TODO gamerforEA code replace, old code:
		ItemStack stack = new ItemStack(Item.getItemById(itemId), stackSize, itemDamage);
		if (hasCompound)
			stack.setTagCompound(readNBT(data)); */
		Item item = Item.getItemById(itemId);
		NBTTagCompound nbt = hasCompound ? readNBT(data) : null;

		if (item == null)
			return null;

		ItemStack stack = new ItemStack(item, stackSize, itemDamage);
		if (nbt != null)
			stack.setTagCompound(nbt);
		// TODO gamerforEA code end

		return stack;
	}

	public static void writeByteArray(ByteBuf stream, byte[] data)
	{
		stream.writeInt(data.length);
		stream.writeBytes(data);
	}

	public static byte[] readByteArray(ByteBuf stream)
	{
		byte[] data = new byte[stream.readInt()];
		stream.readBytes(data, 0, data.length);
		return data;
	}
}
