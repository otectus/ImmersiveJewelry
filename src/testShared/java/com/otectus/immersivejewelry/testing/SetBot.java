package com.otectus.immersivejewelry.testing;

import com.otectus.immersivejewelry.minigame.setting.SettingParams;
import com.otectus.immersivejewelry.minigame.setting.SettingSimulation;

/**
 * A reference jeweler for tests: squeezes each point until one more step would pass the middle of its window, then
 * releases (and pauses a step). With {@code heavy} it squeezes every point far too hard.
 */
public final class SetBot {
    private final SettingSimulation sim;
    private final boolean heavy;
    private int pause;

    public SetBot(SettingParams params, boolean heavy) {
        this.sim = new SettingSimulation(params);
        this.heavy = heavy;
    }

    /** One step's input (squeezing or not). Advances the bot's own simulation. */
    public boolean next() {
        boolean squeeze;
        if (pause > 0) {
            pause--;
            squeeze = false;
        } else {
            int point = sim.activePoint();
            float target = sim.target(point);
            if (!sim.holding()) {
                squeeze = true;
            } else if (heavy) {
                squeeze = sim.current() < target + sim.params().window() * 2.5F;
            } else {
                float step = sim.params().fillRate() * 0.02F * (1F + 0.35F * sim.current());
                squeeze = sim.current() + step < target;
            }
            if (!squeeze) pause = 1;
        }
        sim.step(squeeze);
        return squeeze;
    }

    public SettingSimulation sim() {
        return sim;
    }
}
