package adris.altoclef.multiversion;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class TextVer {


    @Pattern
    public static MutableText empty() {
        //#if MC >= 11900
        return Text.empty();
        //#else
        //$$ return new net.minecraft.text.LiteralText("");
        //#endif
    }

    @Pattern
    public static MutableText literal(String str) {
        //#if MC >= 11900
        return Text.literal(str);
        //#else
        //$$ return new net.minecraft.text.LiteralText(str);
        //#endif
    }

}
