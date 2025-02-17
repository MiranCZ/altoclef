package adris.altoclef.multiversion.world;

import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

public class WorldHelper {

    public static boolean isOutOfHeightLimit(World world,BlockPos pos) {
         return isOutOfHeightLimit(pos.getY());
      }

    private static boolean isOutOfHeightLimit(int y) {
         return y < adris.altoclef.util.helpers.WorldHelper.getBottomY() || y >= adris.altoclef.util.helpers.WorldHelper.getTopY();
      }

}
