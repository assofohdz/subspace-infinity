/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.sim.SimplePhysics;

/**
 *
 * @author Asser
 */
public class ScoreState extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet towerRange;
    private GameOrchestratorState gameOrchestrator;
    private float totalMultiplier;

    private float waveMultiplier;

    private double timeBetweenCalculations = 5;
    private double timeSinceLastCalculation = 0;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        //multiplier is currently equal to the wave number:
        this.gameOrchestrator = getSystem(GameOrchestratorState.class);
    }

    @Override
    protected void terminate() {

    }

    @Override
    public void update(SimTime tpf) {

        if (timeSinceLastCalculation > timeBetweenCalculations) {

            calculateTotalMultiplier();
            timeSinceLastCalculation = 0;
        } else {
            timeSinceLastCalculation += tpf.getTpf();
        }

    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    private void calculateTotalMultiplier() {

        totalMultiplier = 1;
        totalMultiplier *= calculateWaveMultiplier();

        //etc.
    }

    private float calculateWaveMultiplier() {
        waveMultiplier = (float) gameOrchestrator.getWave();

        return waveMultiplier;
    }
}
