package unwrittenfun.minecraft.immersiveintegration.items;

import blusunrize.immersiveengineering.api.*;
import blusunrize.immersiveengineering.common.IEContent;
import blusunrize.immersiveengineering.common.IESaveData;
import blusunrize.immersiveengineering.common.blocks.metal.TileEntityConnectorLV;
import blusunrize.immersiveengineering.common.blocks.wooden.TileEntityWoodenPost;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.Lib;
import blusunrize.immersiveengineering.common.util.Utils;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import unwrittenfun.minecraft.immersiveintegration.ImmersiveIntegration;
import unwrittenfun.minecraft.immersiveintegration.tiles.TileMEWireConnector;
import unwrittenfun.minecraft.immersiveintegration.wires.IIWires;

import java.util.List;

public class ItemCoil extends Item implements IWireCoil {
  public static final int FLUIX_COIL_META = 0;
  public static final int DENSE_COIL_META = 1;

  protected static final String[] COIL_KEYS = new String[] { "Fluix", "Dense" };
  protected static final WireType[] WIRE_TYPES = new WireType[] { IIWires.fluixWire, IIWires.denseWire };

  protected IIcon[] icons;
  protected String key;

  public ItemCoil(String key) {
    this.key = key;
    setTextureName(key);
    setUnlocalizedName(key);
    setHasSubtypes(true);
    setCreativeTab(ImmersiveIntegration.iiCreativeTab);
  }

  @Override
  public void registerIcons(IIconRegister register) {
    icons = new IIcon[COIL_KEYS.length];
    for (int i = 0; i < COIL_KEYS.length; i++) {
      icons[i] = register.registerIcon(key + COIL_KEYS[i]);
    }
  }

  @Override
  public IIcon getIconFromDamage(int meta) {
    meta = Math.min(meta, icons.length - 1);
    return icons[meta];
  }

  @Override
  public String getUnlocalizedName(ItemStack stack) {
    int meta = Math.min(stack.getItemDamage(), COIL_KEYS.length - 1);
    return getUnlocalizedName() + COIL_KEYS[meta];
  }

  @SuppressWarnings("unchecked")
  @Override
  public void getSubItems(Item item, CreativeTabs tab, List items) {
    for (int i = 0; i < COIL_KEYS.length; i++) {
      items.add(new ItemStack(item, 1, i));
    }
  }

  @Override
  public WireType getWireType(ItemStack stack) {
    int meta = Math.min(stack.getItemDamage(), WIRE_TYPES.length - 1);
    return WIRE_TYPES[meta];
  }

  @SuppressWarnings("unchecked")
  @Override
  public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
    if (stack.getTagCompound() != null && stack.getTagCompound().hasKey("linkingPos")) {
      int[] link = stack.getTagCompound().getIntArray("linkingPos");
      if (link != null && link.length > 3)
        list.add(StatCollector.translateToLocalFormatted(Lib.DESC_INFO + "attachedToDim", link[1], link[2], link[3], link[0]));
    }
  }

  @Override
  public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
    if (!world.isRemote && world.getTileEntity(x, y, z) instanceof IImmersiveConnectable && ((IImmersiveConnectable) world.getTileEntity(x, y, z)).canConnect()) {
      TargetingInfo target = new TargetingInfo(side, hitX, hitY, hitZ);
      if (!((IImmersiveConnectable) world.getTileEntity(x, y, z)).canConnectCable(getWireType(stack), target) || (world.getTileEntity(x, y, z) instanceof TileEntityConnectorLV)) {
//        getWireType(stack);
        player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN + "wrongCable"));
        return false;
      }

      if (!ItemNBTHelper.hasKey(stack, "linkingPos")) {
        ItemNBTHelper.setIntArray(stack, "linkingPos", new int[] { world.provider.dimensionId, x, y, z });
        target.writeToNBT(stack.getTagCompound());
      } else {
        WireType type = getWireType(stack);
        int[] pos = ItemNBTHelper.getIntArray(stack, "linkingPos");
        int distance = (int) Math.ceil(Math.sqrt((pos[1] - x) * (pos[1] - x) + (pos[2] - y) * (pos[2] - y) + (pos[3] - z) * (pos[3] - z)));
        if (pos[0] != world.provider.dimensionId)
          player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN + "wrongDimension"));
        else if (pos[1] == x && pos[2] == y && pos[3] == z)
          player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN + "sameConnection"));
        else if (distance > type.getMaxLength())
          player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN + "tooFar"));
        else if (!(world.getTileEntity(x, y, z) instanceof IImmersiveConnectable) || !(world.getTileEntity(pos[1], pos[2], pos[3]) instanceof IImmersiveConnectable))
          player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN + "invalidPoint"));
        else {
          IImmersiveConnectable nodeHere = (IImmersiveConnectable) world.getTileEntity(x, y, z);
          IImmersiveConnectable nodeLink = (IImmersiveConnectable) world.getTileEntity(pos[1], pos[2], pos[3]);
          boolean connectionExists = false;
          if (nodeHere != null && nodeLink != null) {
            for (ImmersiveNetHandler.Connection con : ImmersiveNetHandler.INSTANCE.getConnections(world, Utils.toCC(nodeHere))) {
              if (con.end.equals(Utils.toCC(nodeLink))) connectionExists = true;
            }
          }
          if (connectionExists) player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN + "connectionExists"));
          else {
            Vec3 rtOff0 = nodeHere.getRaytraceOffset().addVector(x, y, z);
            Vec3 rtOff1 = nodeLink.getRaytraceOffset().addVector(pos[1], pos[2], pos[3]);
            boolean canSee = Utils.canBlocksSeeOther(world, new ChunkCoordinates(x, y, z), new ChunkCoordinates(pos[1], pos[2], pos[3]), rtOff0, rtOff1);
            if (canSee) {
              TargetingInfo targetLink = TargetingInfo.readFromNBT(stack.getTagCompound());
              ImmersiveNetHandler.INSTANCE.addConnection(world, Utils.toCC(nodeHere), Utils.toCC(nodeLink), distance, type);
              nodeHere.connectCable(type, target);
              nodeLink.connectCable(type, targetLink);
              IESaveData.setDirty(world.provider.dimensionId);

              if (!player.capabilities.isCreativeMode) stack.stackSize--;
              ((TileEntity) nodeHere).markDirty();
              world.markBlockForUpdate(x, y, z);
              ((TileEntity) nodeLink).markDirty();
              world.markBlockForUpdate(pos[1], pos[2], pos[3]);

              TileEntity tileEntity = world.getTileEntity(x, y, z);
              if (tileEntity instanceof TileMEWireConnector) {
                ((TileMEWireConnector) tileEntity).connectTo(pos[1], pos[2], pos[3]);
              }
            } else player.addChatMessage(new ChatComponentTranslation(Lib.CHAT_WARN + "cantSee"));
          }
        }
        ItemNBTHelper.remove(stack, "linkingPos");
        ItemNBTHelper.remove(stack, "side");
        ItemNBTHelper.remove(stack, "hitX");
        ItemNBTHelper.remove(stack, "hitY");
        ItemNBTHelper.remove(stack, "hitZ");
      }
      return true;
    }
    return false;
  }
}
