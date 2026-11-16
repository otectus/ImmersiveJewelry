package com.otectus.immersivejewelry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.immersivejewelry.client.guide.HelpTopics;
import com.otectus.immersivejewelry.client.guide.Lessons;
import com.otectus.immersivejewelry.client.presentation.anim.JewelerClip;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five libraries' resources against the code that uses them: GeckoLib rigs and clips, playerAnimator clips, the
 * Patchouli book, the Ponder lessons, their structures and captions, and every locale carrying the same keys.
 */
class LibraryResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/immersive_jewelry");
    private static final Path DATA = Path.of("src/main/resources/data/immersive_jewelry");
    private static final List<String> LOCALES = List.of("en_us", "es_es", "pt_br", "ru_ru", "zh_cn");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Map<String, JsonElement> lang(String locale) throws IOException {
        return json(ASSETS.resolve("lang/" + locale + ".json")).asMap();
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/otectus/immersivejewelry/" + relative), StandardCharsets.UTF_8);
    }

    /** String literals of a Java array constant {@code NAME = {"a", "b"}} in a source file. */
    private static Set<String> arrayConstant(String source, String name) {
        int start = source.indexOf(name + " = {");
        int end = source.indexOf("};", start);
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("\"([a-z_]+)\"").matcher(source.substring(start, end));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static Set<String> bones(String rig) throws IOException {
        JsonObject geo = json(ASSETS.resolve("geo/" + rig + ".geo.json"));
        Set<String> out = new HashSet<>();
        for (JsonElement bone : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            out.add(bone.getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    @Test
    void everyRigBoneAndClipTheCodePosesExists() throws IOException {
        String rigs = source("client/presentation/geo/JewelryRigs.java");
        Map<String, String> boneLists = Map.of("lapidary_wheel", "WHEEL_BONES", "jewelers_bench", "BENCH_BONES", "bench_tools", "TOOL_BONES");
        for (var rig : boneLists.entrySet()) {
            Set<String> present = bones(rig.getKey());
            for (String bone : arrayConstant(rigs, rig.getValue())) assertTrue(present.contains(bone), rig.getKey() + " lacks bone " + bone);
            for (JsonElement b : json(ASSETS.resolve("geo/" + rig.getKey() + ".geo.json")).getAsJsonArray("minecraft:geometry").get(0)
                    .getAsJsonObject().getAsJsonArray("bones")) {
                JsonObject bone = b.getAsJsonObject();
                if (bone.has("parent")) assertTrue(present.contains(bone.get("parent").getAsString()), rig.getKey() + ": orphan " + bone);
            }
            assertTrue(Files.exists(ASSETS.resolve("textures/rig/" + rig.getKey() + ".png")), rig.getKey() + " texture");
        }
        assertTrue(Files.exists(ASSETS.resolve("textures/rig/bench_tools_fine.png")));
        Matcher clip = Pattern.compile("\"(lap_seat|lever_pull|clamp_close|pliers_squeeze|brush_dab|cloth_rub)\"").matcher(rigs);
        Set<String> used = new TreeSet<>();
        while (clip.find()) used.add(clip.group(1));
        assertEquals(6, used.size(), "JewelryRigs names six clips");
        Set<String> authored = new HashSet<>();
        for (String rig : boneLists.keySet()) {
            JsonObject anim = json(ASSETS.resolve("animations/" + rig + ".animation.json")).getAsJsonObject("animations");
            for (var entry : anim.entrySet()) {
                authored.add(entry.getKey());
                Set<String> present = bones(rig);
                for (String bone : entry.getValue().getAsJsonObject().getAsJsonObject("bones").keySet()) {
                    assertTrue(present.contains(bone), rig + "#" + entry.getKey() + " animates missing bone " + bone);
                }
            }
        }
        assertTrue(authored.containsAll(used), "every clip the rigs sample is authored: " + used + " vs " + authored);
    }

    @Test
    void everyBodyClipAndItsReducedTwinExist() throws IOException {
        for (JewelerClip clip : JewelerClip.values()) {
            for (String id : new String[]{clip.id(), clip.id() + "_reduced"}) {
                Path path = ASSETS.resolve("player_animation/" + id + ".json");
                assertTrue(Files.exists(path), "missing body clip " + id);
                JsonObject emote = json(path).getAsJsonObject("emote");
                assertEquals(clip.length, emote.get("endTick").getAsInt(), id + " length");
                assertEquals(clip.loop, emote.get("isLoop").getAsBoolean(), id + " loop");
                for (JsonElement move : emote.getAsJsonArray("moves")) {
                    for (String part : move.getAsJsonObject().keySet()) {
                        if (part.equals("tick") || part.equals("easing")) continue;
                        assertTrue(Set.of("head", "torso", "rightArm", "leftArm").contains(part), id + " keys " + part + " (legs stay with the game)");
                    }
                }
            }
        }
    }

    @Test
    void theHandbookIsCompleteAndConsistent() throws IOException {
        Path book = ASSETS.resolve("patchouli_books/jewelers_handbook/en_us");
        JsonObject definition = json(DATA.resolve("patchouli_books/jewelers_handbook/book.json"));
        assertEquals("immersive_jewelry:jewelers_handbook", definition.get("custom_book_item").getAsString());
        assertTrue(definition.get("i18n").getAsBoolean());
        Set<String> categories = new HashSet<>();
        try (var files = Files.list(book.resolve("categories"))) {
            files.forEach(p -> categories.add("immersive_jewelry:" + p.getFileName().toString().replace(".json", "")));
        }
        Set<String> templates = new HashSet<>();
        try (var files = Files.list(book.resolve("templates"))) {
            files.forEach(p -> templates.add("immersive_jewelry:" + p.getFileName().toString().replace(".json", "")));
        }
        Set<String> lessons = new HashSet<>();
        for (Lessons l : Lessons.values()) lessons.add(l.id());
        Set<String> entries = new HashSet<>();
        Map<String, JsonElement> en = lang("en_us");
        int pages = 0;
        try (var files = Files.walk(book.resolve("entries"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = book.resolve("entries").relativize(path).toString().replace('\\', '/').replace(".json", "");
                entries.add(id);
                JsonObject entry = json(path);
                assertTrue(categories.contains(entry.get("category").getAsString()), id + " category");
                assertTrue(en.containsKey(entry.get("name").getAsString()), id + " name key");
                for (JsonElement p : entry.getAsJsonArray("pages")) {
                    JsonObject page = p.getAsJsonObject();
                    String type = page.get("type").getAsString();
                    pages++;
                    if (type.equals("patchouli:text")) {
                        String key = page.get("text").getAsString();
                        assertTrue(en.containsKey(key), id + " text " + key);
                    } else {
                        assertTrue(templates.contains(type), id + " uses missing template " + type);
                        if (type.equals("immersive_jewelry:watch")) assertTrue(lessons.contains(page.get("lesson").getAsString()), id + " lesson");
                    }
                }
            }
        }
        for (String entry : HelpTopics.ENTRIES) assertTrue(entries.contains(entry), "help links to missing entry " + entry);
        // All fourteen chapters of the old handbook live on.
        for (String chapter : List.of("welcome", "stones", "wheel", "cutting", "cuts", "blanks", "bench", "setting", "finishes", "wearing", "effects",
                "care", "designs", "jeweler")) {
            assertTrue(en.containsKey("handbook.immersive_jewelry." + chapter + ".0"), "chapter " + chapter);
        }
        assertTrue(pages > 120, "pages: " + pages);
        // Every custom component class the templates name exists.
        try (var files = Files.list(book.resolve("templates"))) {
            for (Path path : files.toList()) {
                for (JsonElement c : json(path).getAsJsonArray("components")) {
                    JsonObject component = c.getAsJsonObject();
                    if (!component.get("type").getAsString().equals("patchouli:custom")) continue;
                    String cls = component.get("class").getAsString().replace("com.otectus.immersivejewelry.", "").replace('.', '/');
                    assertTrue(Files.exists(Path.of("src/main/java/com/otectus/immersivejewelry/" + cls + ".java")), "component " + cls);
                }
            }
        }
    }

    @Test
    void everyLessonHasItsHeaderTagsAndCaptionsInEveryLocale() throws IOException {
        Map<String, JsonElement> en = lang("en_us");
        String scenes = source("client/compat/ponder/LessonScenes.java") + source("client/compat/ponder/SceneKit.java");
        for (Lessons lesson : Lessons.values()) {
            String p = "immersive_jewelry.ponder.";
            assertTrue(en.containsKey(p + lesson.id() + ".header"), lesson.id() + " header");
            assertTrue(en.containsKey(p + "tag.lesson/" + lesson.id()), lesson.id() + " tag");
            assertTrue(en.containsKey("lesson.immersive_jewelry." + lesson.id()), lesson.id() + " name");
            assertTrue(en.containsKey(p + "shared." + lesson.id() + ".0"), lesson.id() + " first caption");
            int n = 0;
            while (en.containsKey(p + "shared." + lesson.id() + "." + n)) n++;
            assertTrue(n >= 1);
        }
        for (Lessons.Topic topic : Lessons.Topic.values()) {
            assertTrue(en.containsKey("immersive_jewelry.ponder.tag." + topic.id().getPath()), topic + " tag");
        }
        assertTrue(en.containsKey("immersive_jewelry.ponder.shared.example"));
        for (String layout : List.of("wheel", "bench", "workshop")) {
            assertTrue(Files.exists(ASSETS.resolve("ponder/" + layout + ".nbt")), "structure " + layout);
            assertTrue(scenes.contains("\"" + layout + "\""), "layout " + layout + " used");
        }
    }

    @Test
    void everyLocaleCarriesTheSameKeys() throws IOException {
        Set<String> base = lang("en_us").keySet();
        for (String locale : LOCALES) {
            Set<String> keys = lang(locale).keySet();
            Set<String> missing = new TreeSet<>(base);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(base);
            assertTrue(missing.isEmpty(), locale + " lacks " + missing);
            assertTrue(extra.isEmpty(), locale + " has extra " + extra);
        }
    }

    @Test
    void theLibrariesAreDeclaredWithTheirSides() throws IOException {
        String toml = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"), StandardCharsets.UTF_8);
        for (String both : List.of("patchouli", "geckolib", "diegesis")) {
            assertTrue(Pattern.compile("modId=\"" + both + "\"\\s+mandatory=true[\\s\\S]*?side=\"BOTH\"").matcher(toml).find(), both);
        }
        for (String client : List.of("ponder", "flywheel", "playeranimator")) {
            assertTrue(Pattern.compile("modId=\"" + client + "\"\\s+mandatory=true[\\s\\S]*?side=\"CLIENT\"").matcher(toml).find(), client);
        }
        // Common code never reaches the client-only libraries.
        try (var files = Files.walk(Path.of("src/main/java/com/otectus/immersivejewelry"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = path.toString().replace('\\', '/');
                if (rel.contains("/client/")) continue;
                String text = Files.readString(path, StandardCharsets.UTF_8);
                assertFalse(text.contains("net.createmod") || text.contains("dev.kosmx") || text.contains("vazkii.patchouli"),
                        rel + " is common code and must not reference Ponder, playerAnimator or Patchouli");
            }
        }
    }
}
