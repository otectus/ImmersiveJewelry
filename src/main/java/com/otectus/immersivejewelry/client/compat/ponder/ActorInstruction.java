package com.otectus.immersivejewelry.client.compat.ponder;

import net.createmod.ponder.foundation.instruction.FadeIntoSceneInstruction;
import net.minecraft.core.Direction;

/** Brings the demonstrating jeweler into a scene (faded in, as Ponder brings in its own figures). */
final class ActorInstruction extends FadeIntoSceneInstruction<JewelerActor> {
    ActorInstruction(int ticks, Direction from, JewelerActor actor) {
        super(ticks, from, actor);
    }

    @Override
    protected Class<JewelerActor> getElementClass() {
        return JewelerActor.class;
    }
}
