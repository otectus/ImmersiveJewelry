package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Precision-work sounds: no hammer blows. Every event has its own synthesised recording (see art/jewelry_audio.py). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ImmersiveJewelry.MOD_ID);

    public static final RegistryObject<SoundEvent> WHEEL_MOUNT = register("block.lapidary_wheel.mount");
    public static final RegistryObject<SoundEvent> WHEEL_SPIN = register("block.lapidary_wheel.spin");
    public static final RegistryObject<SoundEvent> WHEEL_GRIND = register("block.lapidary_wheel.grind");
    public static final RegistryObject<SoundEvent> WHEEL_CHIP = register("block.lapidary_wheel.chip");
    public static final RegistryObject<SoundEvent> WHEEL_FACET = register("block.lapidary_wheel.facet");
    public static final RegistryObject<SoundEvent> WHEEL_FINISH = register("block.lapidary_wheel.finish");
    public static final RegistryObject<SoundEvent> WHEEL_LAP = register("block.lapidary_wheel.lap");

    public static final RegistryObject<SoundEvent> BENCH_PLACE = register("block.jewelers_bench.place");
    public static final RegistryObject<SoundEvent> BENCH_PRONG = register("block.jewelers_bench.prong");
    public static final RegistryObject<SoundEvent> BENCH_SEAT = register("block.jewelers_bench.seat");
    public static final RegistryObject<SoundEvent> BENCH_BEND = register("block.jewelers_bench.bend");
    public static final RegistryObject<SoundEvent> BENCH_POLISH = register("block.jewelers_bench.polish");
    public static final RegistryObject<SoundEvent> BENCH_FINISH = register("block.jewelers_bench.finish");
    public static final RegistryObject<SoundEvent> BENCH_REPAIR = register("block.jewelers_bench.repair");

    public static final RegistryObject<SoundEvent> JEWELRY_EQUIP = register("item.jewelry.equip");
    public static final RegistryObject<SoundEvent> SKETCH_LEARN = register("item.design_sketch.learn");
    public static final RegistryObject<SoundEvent> EFFECT_TRIGGER = register("jewelry.effect.trigger");
    public static final RegistryObject<SoundEvent> EFFECT_WARD = register("jewelry.effect.ward");
    public static final RegistryObject<SoundEvent> EFFECT_SENSE = register("jewelry.effect.sense");

    public static final RegistryObject<SoundEvent> UI_CUE = register("ui.cue");
    public static final RegistryObject<SoundEvent> ACTION_FAIL = register("ui.fail");
    public static final RegistryObject<SoundEvent> VILLAGER_WORK_JEWELER = register("entity.villager.work_jeweler");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ImmersiveJewelry.id(name)));
    }

    private ModSounds() {}
}
