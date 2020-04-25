/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.client;
import com.simsilica.mathd.trans.Transition;
import com.simsilica.mathd.trans.TransitionBuffer;
import java.util.Arrays;
/**
 * Holds a set of transitions and can return a transition spanning a specific
 * time index. This data structure is semi-thread safe in that it will support
 * one writer and many readers with very low overhead (no internal
 * synchronization is used).
 *
 * @author Paul Speed
 */
public class MyTransitionBuffer extends TransitionBuffer {
    public MyTransitionBuffer(int size) {
        super(size);
    }
}