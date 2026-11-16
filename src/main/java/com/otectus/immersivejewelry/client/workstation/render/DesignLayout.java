package com.otectus.immersivejewelry.client.workstation.render;

import com.otectus.immersivejewelry.recipe.SettingStyle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a design's stones sit on its frame, in the frame's own pixels: the frame stands upright in the bench clamp facing
 * the worker (+y up, -z towards the camera), centred on its origin and about seven pixels across. Purely visual: the
 * server knows sockets only by index and judges what the tools did in each socket's own normalised coordinates.
 *
 * <p>The ten shipped designs have authored layouts; any other design gets a generic one from its form and stone count,
 * so datapack designs stay workable.</p>
 */
public record DesignLayout(Body body, List<Socket> sockets) {

    /** The frame's silhouette. */
    public enum Body {
        /** A ring band seen face-on, its head at the top. */
        RING,
        /** A round pendant plate with a bail. */
        PENDANT,
        /** An oval locket with a hinge and bail. */
        LOCKET,
        /** A pendant with a decorative halo round its centre stone. */
        HALO,
        /** A wide cuff, its front panel at the bottom of the view. */
        CUFF,
        /** A chain of links along a curve. */
        LINKS,
        /** A slim circlet band with its front setting at the top. */
        CIRCLET,
        /** A crown band rising to three peaks. */
        DIADEM
    }

    /**
     * One setting position: centre, the stone's girdle radius, and the turn of the cup (non-round cuts align to it).
     */
    public record Socket(float x, float y, float radius, float turn) {}

    public static DesignLayout of(ResourceLocation form, ResourceLocation design, int stones, SettingStyle style) {
        DesignLayout authored = design.getNamespace().equals("immersive_jewelry") ? authored(design.getPath().replace("designs/", "")) : null;
        return authored != null && authored.sockets().size() == stones ? authored : generic(form, stones, style);
    }

    private static DesignLayout authored(String path) {
        return switch (path) {
            case "solitaire_ring", "bezel_ring" -> new DesignLayout(Body.RING, List.of(new Socket(0F, 4.6F, 1.45F, 0F)));
            case "trilogy_ring" -> new DesignLayout(Body.RING, List.of(new Socket(0F, 4.7F, 1.3F, 0F),
                    new Socket(-2.55F, 4.0F, 0.95F, -0.35F), new Socket(2.55F, 4.0F, 0.95F, 0.35F)));
            case "pendant_amulet" -> new DesignLayout(Body.PENDANT, List.of(new Socket(0F, -0.6F, 1.9F, 0F)));
            case "locket_amulet" -> new DesignLayout(Body.LOCKET, List.of(new Socket(0F, -0.7F, 1.85F, 0F)));
            case "halo_amulet" -> new DesignLayout(Body.HALO, List.of(new Socket(0F, -0.4F, 1.35F, 0F),
                    new Socket(-2.9F, -1.9F, 0.85F, -0.5F), new Socket(2.9F, -1.9F, 0.85F, 0.5F)));
            case "cuff_bracelet" -> new DesignLayout(Body.CUFF, List.of(new Socket(0F, -2.4F, 1.65F, 0F)));
            case "link_bracelet" -> new DesignLayout(Body.LINKS, List.of(new Socket(0F, -3.0F, 1.15F, 0F),
                    new Socket(-3.3F, -1.7F, 1.05F, -0.6F), new Socket(3.3F, -1.7F, 1.05F, 0.6F)));
            case "simple_circlet" -> new DesignLayout(Body.CIRCLET, List.of(new Socket(0F, 4.3F, 1.45F, 0F)));
            case "diadem" -> new DesignLayout(Body.DIADEM, List.of(new Socket(0F, 5.0F, 1.3F, 0F),
                    new Socket(-3.1F, 3.6F, 1.0F, -0.4F), new Socket(3.1F, 3.6F, 1.0F, 0.4F)));
            default -> null;
        };
    }

    /** Stones spread along the form's main curve. */
    public static DesignLayout generic(ResourceLocation form, int stones, SettingStyle style) {
        int n = Mth.clamp(stones, 1, 8);
        Body body = switch (form.getPath()) {
            case "amulet" -> Body.PENDANT;
            case "bracelet" -> Body.CUFF;
            case "circlet" -> Body.CIRCLET;
            default -> Body.RING;
        };
        boolean top = body == Body.RING || body == Body.CIRCLET;
        float radius = body == Body.PENDANT ? 2.6F : 4.6F;
        float centreY = body == Body.PENDANT ? -0.6F : body == Body.CUFF ? 1.8F : 0F;
        float stone = Mth.clamp(1.6F - 0.14F * n, 0.6F, 1.6F);
        float spread = n == 1 ? 0F : Math.min(Mth.PI * 0.8F, 0.55F * (n - 1));
        List<Socket> sockets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0F : (i / (float) (n - 1) - 0.5F) * spread;
            float a = (top ? Mth.HALF_PI : -Mth.HALF_PI) + t;
            if (body == Body.PENDANT && n == 1) {
                sockets.add(new Socket(0F, centreY, 1.9F, 0F));
                continue;
            }
            sockets.add(new Socket(Mth.cos(a) * radius, centreY + Mth.sin(a) * radius, stone, t));
        }
        // Put the principal stone first: the centre of the curve.
        sockets.sort((x, y) -> Float.compare(Math.abs(x.turn()), Math.abs(y.turn())));
        return new DesignLayout(body, sockets);
    }

    /** The socket at index {@code i}, or the last one when a design has more stones than this layout (never empty). */
    public Socket socket(int i) {
        return sockets.get(Mth.clamp(i, 0, sockets.size() - 1));
    }
}
