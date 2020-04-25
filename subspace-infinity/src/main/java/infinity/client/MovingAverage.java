/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.client;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
/**
 *
 * @author asser
 */
public class MovingAverage {
    private final Queue<Double> window = new ArrayDeque<>();
    private final int period;
    private double sum = 0d;
    public MovingAverage(int period) {
        this.period = period;
    }
    public void add(Double num) {
        sum = sum + num;
        window.add(num);
        if (window.size() > period) {
            sum = sum - window.remove();
        }
    }
    public double getAverage() {
        if (window.isEmpty()) {
            return 0d;
        }
        return sum / Double.valueOf(window.size());
    }
    public List<Double> getList() {
        return new ArrayList(window);
    }
}