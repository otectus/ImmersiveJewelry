package com.otectus.immersivejewelry;

import com.otectus.immersivejewelry.client.workstation.render.GemGeometry;
import com.otectus.immersivejewelry.client.workstation.render.DesignLayout;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import net.minecraft.resources.ResourceLocation;
import com.otectus.immersivejewelry.client.workstation.render.GemProgress;
import com.otectus.immersivejewelry.client.workstation.render.Polyhedron;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GemVisualContinuityTest {
    private static final List<GemGeometry.Profile> PROFILES = List.of(GemGeometry.Profile.ROUND, GemGeometry.Profile.BRILLIANT,
            GemGeometry.Profile.EMERALD, GemGeometry.Profile.CABOCHON);

    private static Vector3f[] directions() {
        Random random = new Random(71);
        Vector3f[] directions = new Vector3f[96];
        for (int i = 0; i < directions.length; i++) directions[i] = new Vector3f(random.nextFloat() - .5F,
                random.nextFloat() - .5F, random.nextFloat() - .5F).normalize();
        return directions;
    }

    private static int facets(GemGeometry.Profile profile) {
        return switch (profile) { case ROUND -> 6; case BRILLIANT -> 10; case EMERALD -> 8; default -> 4; };
    }

    @Test void braceletSettingsClearTheViseBase() {
        for (String design : List.of("cuff_bracelet", "link_bracelet")) {
            var layout = DesignLayout.of(new ResourceLocation("immersive_jewelry", "bracelet"),
                    new ResourceLocation("immersive_jewelry", "designs/" + design), design.startsWith("link") ? 3 : 1, SettingStyle.PRONG);
            for (var socket : layout.sockets()) {
                float bottom = 17F + (socket.y() - socket.radius()) * 0.5F;
                assertTrue(bottom > 14.65F, design + " embeds a stone in the vise base");
            }
        }
    }

    @Test void dopBondsToTheSurfaceOnItsAxisRatherThanAFartherOffAxisVertex() {
        for (long seed = 0; seed < 32; seed++) {
            Polyhedron rough = GemGeometry.rough(seed);
            Vector3f mount = new Vector3f(0, -rough.surfaceRadius(new Vector3f(0, -1, 0)), 0);
            float nearest = Float.POSITIVE_INFINITY;
            for (var face : rough.faces()) {
                float gap = face.points().get(0).dot(face.normal()) - mount.dot(face.normal());
                assertTrue(gap >= -1e-5F, "dop floats outside the stone");
                nearest = Math.min(nearest, Math.abs(gap));
            }
            assertEquals(0F, nearest, 1e-5F);
        }
    }

    @Test void cutsThroughExistingEdgesStillProduceAClosedCap() {
        Vector3f normal = new Vector3f(1, 1, 0).normalize();
        Polyhedron clipped = Polyhedron.box(1, 1, 1, Polyhedron.ROUGH).clip(normal, 0F, 81);
        var cap = clipped.faces().stream().filter(f -> f.tag() == 81).findFirst().orElseThrow();
        assertEquals(4, cap.points().size());
        for (var point : cap.points()) assertEquals(0F, point.dot(normal), 1e-6F);
        assertEquals(0F, clipped.support(normal), 1e-6F);
        assertEquals((float) Math.sqrt(2), clipped.support(new Vector3f(normal).negate()), 1e-6F);
    }

    @Test void planningAnyCutPreservesTheUncutStone() {
        Polyhedron rough = GemGeometry.rough(42);
        for (var profile : PROFILES) {
            float[] targets = new float[facets(profile)]; Arrays.fill(targets, 1F);
            Polyhedron planned = GemGeometry.shape(profile, 42, new float[targets.length], targets, 0, 0F);
            for (var direction : directions()) assertEquals(rough.support(direction), planned.support(direction), 1e-6F, profile.toString());
        }
    }

    @Test void grindingAnotherFacetNeverAddsMaterialBackToTheStone() {
        for (var profile : PROFILES) {
            float[] targets = new float[facets(profile)]; Arrays.fill(targets, 1F);
            float[] depths = new float[targets.length];
            Polyhedron before = GemGeometry.shape(profile, 42, depths, targets, 0, 0F);
            for (int step = 1; step <= targets.length * 20; step++) {
                depths[(step - 1) / 20] += .05F;
                Polyhedron after = GemGeometry.shape(profile, 42, depths, targets, 0, step / (targets.length * 20F));
                for (var direction : directions()) assertTrue(after.support(direction) <= before.support(direction) + 2e-5F,
                        profile + " regrew at step " + step);
                before = after;
            }
        }
    }

    @Test void finishedCutsHaveCleanSymmetricSilhouettesWithoutRoughSkin() {
        for (var profile : PROFILES) {
            Polyhedron a = GemGeometry.finished(profile, 42, facets(profile));
            Polyhedron b = GemGeometry.finished(profile, 712, facets(profile));
            assertFalse(a.faces().isEmpty());
            assertTrue(a.faces().stream().noneMatch(f -> f.tag() == Polyhedron.ROUGH), profile + " retains rough skin");
            for (var direction : directions()) assertEquals(a.support(direction), b.support(direction), 1e-4F, profile.toString());
        }
    }

    @Test void cacheEvictionCannotChangeAnIdenticalQuantizedShape() {
        float[] targets = new float[8]; Arrays.fill(targets, 1F);
        float[] a = new float[8], b = new float[8];
        Arrays.fill(a, .5001F); Arrays.fill(b, .5002F);
        GemGeometry.clear();
        Polyhedron first = GemGeometry.shape(GemGeometry.Profile.ROUND, 42, a, targets, 0, .5001F);
        GemGeometry.clear();
        Polyhedron second = GemGeometry.shape(GemGeometry.Profile.ROUND, 42, b, targets, 0, .5002F);
        for (var direction : directions()) assertEquals(first.support(direction), second.support(direction), 1e-6F);
    }

    @Test void confirmedProgressEasesForwardAndIndexingTakesTheShortArc() {
        GemProgress progress = new GemProgress();
        progress.update(1, new float[8], 0, 7, 0, .016F);
        float angle = progress.snapshot().indexAngle();
        progress.update(1, new float[]{.5F,0,0,0,0,0,0,0}, .0625F, 0, 0, .016F);
        var next = progress.snapshot();
        assertTrue(next.ratios()[0] > 0 && next.ratios()[0] < .5F);
        assertTrue(next.indexAngle() > angle && next.indexAngle() - angle < .2F);
        progress.update(1, new float[8], 0, 0, 0, .016F);
        assertTrue(progress.snapshot().ratios()[0] >= next.ratios()[0]);
        progress.update(2, new float[8], 0, 0, 0, .016F);
        assertEquals(0F, progress.snapshot().ratios()[0]);
    }
}
