package com.feed_the_beast.mods.ftbchunks.client.map;

import com.feed_the_beast.mods.ftbchunks.impl.XZ;
import com.feed_the_beast.mods.ftbchunks.net.SendChunkPacket;
import net.minecraft.block.AbstractButtonBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.TallGrassBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * @author LatvianModder
 */
public class MapChunk
{
	public final MapRegion region;
	public final XZ pos;
	public long modified;

	public UUID ownerId;
	public Date claimedDate;
	public Date forceLoadedDate;
	public int color;
	public ITextComponent owner;

	public MapChunk(MapRegion r, XZ p)
	{
		region = r;
		pos = p;
		modified = 0L;

		ownerId = null;
		claimedDate = null;
		forceLoadedDate = null;
		color = 0;
		owner = StringTextComponent.EMPTY;
	}

	public int getHRGB(int x, int z)
	{
		int c = region.getDataImage().getPixelRGBA(pos.x * 16 + (x & 15) + 1, pos.z * 16 + (z & 15) + 1);
		int a = NativeImage.getAlpha(c);
		int r = NativeImage.getRed(c);
		int g = NativeImage.getGreen(c);
		int b = NativeImage.getBlue(c);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public boolean connects(MapChunk chunk)
	{
		return (color & 0xFFFFFF) == (chunk.color & 0xFFFFFF) && Objects.equals(ownerId, chunk.ownerId);
	}

	public XZ getActualPos()
	{
		return XZ.of((region.pos.x << 5) + pos.x, (region.pos.z << 5) + pos.z);
	}

	public int getHeight(int x, int z)
	{
		return (getHRGB(x, z) >> 24) & 0xFF;
	}

	public boolean setHRGB(int x, int z, int c)
	{
		int c0 = getHRGB(x, z);

		if (c0 != c)
		{
			int a = (c >> 24) & 0xFF;
			int r = (c >> 16) & 0xFF;
			int g = (c >> 8) & 0xFF;
			int b = (c >> 0) & 0xFF;
			int cc = NativeImage.getCombined(a, b, g, r);
			int ax = pos.x * 16 + (x & 15);
			int az = pos.z * 16 + (z & 15);
			region.getDataImage().setPixelRGBA(ax + 1, az + 1, cc);

			// edges //

			if (ax == 0)
			{
				region.offset(-1, 0).setPixelAndUpdate(513, az + 1, cc);
			}

			if (ax == 511)
			{
				region.offset(1, 0).setPixelAndUpdate(0, az + 1, cc);
			}

			if (az == 0)
			{
				region.offset(0, -1).setPixelAndUpdate(ax + 1, 513, cc);
			}

			if (az == 511)
			{
				region.offset(0, 1).setPixelAndUpdate(ax + 1, 0, cc);
			}

			// corners //

			if (ax == 0 && az == 0)
			{
				region.offset(-1, -1).setPixelAndUpdate(513, 513, cc);
			}

			if (ax == 511 && az == 511)
			{
				region.offset(1, 1).setPixelAndUpdate(0, 0, cc);
			}

			if (ax == 511 && az == 0)
			{
				region.offset(1, -1).setPixelAndUpdate(0, 513, cc);
			}

			if (ax == 0 && az == 511)
			{
				region.offset(-1, 1).setPixelAndUpdate(513, 0, cc);
			}

			return true;
		}

		return false;
	}

	public static boolean skipBlock(BlockState state, IWorld world, BlockPos pos)
	{
		Block b = state.getBlock();

		if (b == Blocks.AIR || b == Blocks.TALL_GRASS || b == Blocks.LARGE_FERN || b instanceof TallGrassBlock || b == Blocks.IRON_BARS)
		{
			return true;
		}
		else if (b instanceof FireBlock)
		{
			return true;
		}
		else if (b instanceof AbstractButtonBlock)
		{
			return true;
		}
		else if (b instanceof TorchBlock)
		{
			return !(b instanceof RedstoneTorchBlock);
		}

		return b.isAir(state, world, pos);
	}

	public static BlockPos.Mutable setHeight(@Nullable IChunk chunk, BlockPos.Mutable pos)
	{
		int topY = pos.getY();

		if (topY == -1 || chunk == null || chunk.getWorldForge() == null)
		{
			pos.setY(-1);
			return pos;
		}

		for (int by = topY; by > 0; by--)
		{
			pos.setY(by);
			BlockState state = chunk.getBlockState(pos);

			if (by == topY || state.getBlock() == Blocks.BEDROCK)
			{
				for (; by > 0; by--)
				{
					pos.setY(by);
					state = chunk.getBlockState(pos);

					if (state.getBlock().isAir(state, chunk.getWorldForge(), pos))
					{
						break;
					}
				}
			}

			if (!skipBlock(state, chunk.getWorldForge(), pos))
			{
				pos.setY(by);
				return pos;
			}
		}

		pos.setY(-1);
		return pos;
	}

	public MapChunk created()
	{
		region.update(true);
		return this;
	}

	public MapChunk offset(int x, int z)
	{
		return region.dimension.getChunk(getActualPos().offset(x, z));
	}

	public void updateFrom(Date now, SendChunkPacket.SingleChunk packet)
	{
		ownerId = packet.ownerId;
		claimedDate = packet.owner == null ? null : new Date(now.getTime() - packet.relativeTimeClaimed);
		forceLoadedDate = packet.forceLoaded && claimedDate != null ? new Date(now.getTime() - packet.relativeTimeForceLoaded) : null;
		color = packet.color;
		owner = packet.owner == null ? StringTextComponent.EMPTY : packet.owner;
		region.update(false);
	}
}