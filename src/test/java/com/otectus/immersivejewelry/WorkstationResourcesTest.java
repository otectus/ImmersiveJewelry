package com.otectus.immersivejewelry;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkstationResourcesTest {
    private static final String ROOT = "/assets/immersive_jewelry/";
    private static final List<String> MODELS = List.of("jewelers_bench", "lapidary_wheel", "lapidary_wheel_disc", "lapidary_wheel_full");
    private static final String[] FACES = {"west", "east", "down", "up", "north", "south"};
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([s%])");

    @Test
    void bundledJsonIsStrictAndHasNoDuplicateKeys() throws IOException {
        try (var paths = Files.walk(Path.of("src/main/resources"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (var reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
                    reader.setLenient(false);
                    readJson(reader, path.toString());
                    assertEquals(JsonToken.END_DOCUMENT, reader.peek(), path.toString());
                }
            }
        }
    }

    private static void readJson(JsonReader reader, String path) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var keys = new HashSet<String>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    assertFalse(!keys.add(key), path + ": duplicate " + key);
                    readJson(reader, path + "." + key);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) readJson(reader, path);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IOException("Unexpected JSON token at " + path);
        }
    }

    @Test
    void wheelItemContainsExactlyTheFrameAndDisc() throws IOException {
        JsonObject frame = json("models/block/lapidary_wheel.json");
        JsonObject disc = json("models/block/lapidary_wheel_disc.json");
        JsonObject full = json("models/block/lapidary_wheel_full.json");
        var elements = frame.getAsJsonArray("elements").deepCopy();
        elements.addAll(disc.getAsJsonArray("elements"));
        assertEquals(elements, full.getAsJsonArray("elements"));
        for (var variant : json("blockstates/lapidary_wheel.json").getAsJsonObject("variants").entrySet()) {
            assertEquals("immersive_jewelry:block/lapidary_wheel", variant.getValue().getAsJsonObject().get("model").getAsString());
        }
    }

    @Test
    void opaqueWorkstationsUseSolidRendering() throws IOException {
        for (String name : MODELS) {
            JsonObject model = json("models/block/" + name + ".json");
            assertEquals("minecraft:solid", model.get("render_type").getAsString(), name);
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String path = "/assets/" + texture.getValue().getAsString().replace(":", "/textures/") + ".png";
                try (InputStream stream = resource(path)) {
                    var image = ImageIO.read(stream);
                    assertNotNull(image, path);
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            assertEquals(255, image.getRGB(x, y) >>> 24, path + " must remain opaque for solid rendering");
                        }
                    }
                }
            }
        }
    }

    @Test
    void workstationFacesDoNotFightForTheSamePlane() throws IOException {
        for (String name : MODELS) {
            var elements = json("models/block/" + name + ".json").getAsJsonArray("elements");
            for (int i = 0; i < elements.size(); i++) {
                JsonObject a = elements.get(i).getAsJsonObject();
                assertFalse(a.getAsJsonObject("faces").size() == 0, name + ": empty element " + a.get("name"));
                // Rotated braces and apron folds do not lie on the cuboids' axis-aligned planes.
                if (a.has("rotation")) continue;
                for (int j = i + 1; j < elements.size(); j++) {
                    JsonObject b = elements.get(j).getAsJsonObject();
                    if (b.has("rotation")) continue;
                    for (int face = 0; face < FACES.length; face++) {
                        String direction = FACES[face];
                        if (!a.getAsJsonObject("faces").has(direction) || !b.getAsJsonObject("faces").has(direction)) continue;
                        int axis = face / 2;
                        String edge = face % 2 == 0 ? "from" : "to";
                        if (Math.abs(coord(a, edge, axis) - coord(b, edge, axis)) > 1e-6) continue;
                        boolean overlaps = true;
                        for (int other = 0; other < 3; other++) {
                            if (other == axis) continue;
                            overlaps &= Math.min(coord(a, "to", other), coord(b, "to", other))
                                    - Math.max(coord(a, "from", other), coord(b, "from", other)) > 1e-6;
                        }
                        assertFalse(overlaps, name + ": " + a.get("name") + " and " + b.get("name") + " overlap on " + direction);
                    }
                }
            }
        }
    }

    @Test
    void russianTranslationCoversEveryKeyAndPreservesArguments() throws IOException {
        JsonObject english = json("lang/en_us.json");
        JsonObject russian = json("lang/ru_ru.json");
        assertEquals(english.keySet(), russian.keySet());
        for (String key : english.keySet()) {
            String translated = russian.get(key).getAsString();
            assertFalse(translated.isBlank(), key);
            assertEquals(arguments(english.get(key).getAsString()), arguments(translated), key);
            assertEquals(formatting(english.get(key).getAsString()), formatting(translated), key + " formatting codes");
        }
    }

    @Test
    void bundledDefinitionsAndAdvancementsReferenceTranslatedText() throws IOException {
        JsonObject english = json("lang/en_us.json");
        try (var paths = Files.walk(Path.of("src/main/resources/data"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(path)) {
                    translationReferences(JsonParser.parseReader(reader), english, path.toString());
                }
            }
        }
        translationReferences(json("sounds.json"), english, "sounds.json");
    }

    private static void translationReferences(JsonElement value, JsonObject english, String path) {
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(e -> translationReferences(e, english, path));
        } else if (value.isJsonObject()) {
            for (var field : value.getAsJsonObject().entrySet()) {
                if (List.of("translate", "name", "description", "adjective", "theme", "subtitle").contains(field.getKey())
                        && field.getValue().isJsonPrimitive() && field.getValue().getAsJsonPrimitive().isString()) {
                    String key = field.getValue().getAsString();
                    if (key.matches("[a-z_]+\\.(immersive_jewelry|minecraft|forge)\\.[a-z_.]+")) {
                        assertFalse(!english.has(key), path + ": missing " + key);
                    }
                }
                translationReferences(field.getValue(), english, path);
            }
        }
    }

    private static List<String> formatting(String value) {
        return Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE).matcher(value).results().map(m -> m.group()).sorted().toList();
    }

    private static List<String> arguments(String text) {
        var matcher = FORMAT.matcher(text);
        List<String> arguments = new ArrayList<>();
        int next = 1;
        while (matcher.find()) {
            arguments.add(matcher.group(2).equals("%") ? "%"
                    : (matcher.group(1) == null ? next++ : Integer.parseInt(matcher.group(1))) + ":s");
        }
        assertFalse(FORMAT.matcher(text).replaceAll("").contains("%"), "Invalid format: " + text);
        Collections.sort(arguments);
        return arguments;
    }

    private static double coord(JsonObject element, String edge, int axis) {
        return element.getAsJsonArray(edge).get(axis).getAsDouble();
    }

    private static JsonObject json(String path) throws IOException {
        try (var reader = new InputStreamReader(resource(ROOT + path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = WorkstationResourcesTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }
}
