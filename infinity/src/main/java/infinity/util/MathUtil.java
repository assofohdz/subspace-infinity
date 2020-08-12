/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.util;

/**
 *
 * @author asser
 */
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.TDistributionImpl;
import org.apache.commons.math.stat.StatUtils;

public class MathUtil {

    /**
     * static instance
     */
    private static final MathUtil instance = new MathUtil();
    /**
     * default significance level
     */
    public static final double DEFAULT_SIGNIFICANCE_LEVEL = 0.95;
    public static final double DEFAULT_09999 = 0.9999;
    public static final double DEFAULT_0999 = 0.999;
    public static final double DEFAULT_099 = 0.99;
    public static final double DEFAULT_085 = 0.85;
    public static final double DEFAULT_075 = 0.75;
    public static final double DEFAULT_065 = 0.65;
    public static final double DEFAULT_055 = 0.55;
    public static final double DEFAULT_045 = 0.45;
    public static final double DEFAULT_035 = 0.35;
    public static final double DEFAULT_025 = 0.25;
    public static final double DEFAULT_015 = 0.15;
    public static final double DEFAULT_005 = 0.05;

    public boolean hasOutlier(Object... values) {
        return hasOutlier(Arrays.asList(values));
    }

    public boolean hasOutlier(List<?> values) {
        return getOutlier(values) != null;
    }

    public boolean hasOutlier(List<?> values, double significanceLevel) {
        return getOutlier(values, significanceLevel) != null;
    }

    /**
     * Returns a statistical outlier with the default significance level (0.95), or
     * null if no such outlier exists..
     */
    public <T> T getOutlier(List<T> values) {
        return getOutlier(values, DEFAULT_SIGNIFICANCE_LEVEL);
    }

    public <T> T getOutlier(List<T> values, double significanceLevel) {
        AtomicReference<T> outlier = new AtomicReference<T>();
        double grubbs = getGrubbsTestStatistic(values, outlier);
        double size = values.size();
        if (size < 3) {
            return null;
        }
        TDistributionImpl t = new TDistributionImpl(size - 2.0);
        try {
            double criticalValue = t.inverseCumulativeProbability((1.0 - significanceLevel) / (2.0 * size));
            double criticalValueSquare = criticalValue * criticalValue;
            double grubbsCompareValue = ((size - 1) / Math.sqrt(size))
                    * Math.sqrt((criticalValueSquare) / (size - 2.0 + criticalValueSquare));
            // System.out.println("critical value: " + grubbs + " - " + grubbsCompareValue);
            if (grubbs > grubbsCompareValue) {
                return outlier.get();
            } else {
                return null;
            }
        } catch (MathException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * returns a minimum outlier (if one exists)
     */
    public <T> T getOutlierMin(List<T> values) {
        T d = getOutlier(values, DEFAULT_SIGNIFICANCE_LEVEL);
        if (d == null) {
            return null;
        }
        double d1 = toDouble(d);
        double d2 = toDouble(min(values));
        if (d1 == d2) {
            return d;
        }
        return null;
    }

    /**
     * returns a minimum outlier (if one exists)
     */
    public <T> T getOutlierMax(List<T> values) {
        T d = getOutlier(values, DEFAULT_SIGNIFICANCE_LEVEL);
        if (d == null) {
            return null;
        }
        double d1 = toDouble(d);
        double d2 = toDouble(max(values));
        if (d1 == d2) {
            return d;
        }
        return null;
    }

    public <T> double getGrubbsTestStatistic(List<T> values, AtomicReference<T> outlier) {
        double[] array = toArray(values);
        double mean = StatUtils.mean(array);
        double stddev = stdDev(values);
        double maxDev = 0;
        for (T o : values) {
            double d = toDouble(o);
            if (Math.abs(mean - d) > maxDev) {
                maxDev = Math.abs(mean - d);
                outlier.set(o);
            }
        }
        double grubbs = maxDev / stddev;
        // System.out.println("mean/stddev/maxDev/grubbs: " + mean + " - " + stddev + "
        // - " + maxDev + " - " + grubbs);
        return grubbs;
    }

    private static enum Operator {
        MIN, MAX
    }

    public double sum(Collection<?> c) {
        double s = 0;
        for (Object o : c) {
            s += Double.parseDouble("" + o);
        }
        return s;
    }

    public double average(Collection<?> c) {
        return sum(c) / (double) c.size();
    }

    public double avg(Collection<?> c) {
        return average(c);
    }

    public <T> T min(List<T> values) {
        return executeOp(values, Operator.MIN);
    }

    public <T> T max(List<T> values) {
        return executeOp(values, Operator.MAX);
    }

    public double max(double[] values) {
        return executeOp(asList(values), Operator.MAX);
    }

    public int max(int[] values) {
        return executeOp(asList(values), Operator.MAX);
    }

    public long max(long[] values) {
        return executeOp(asList(values), Operator.MAX);
    }

    private <T> T executeOp(List<T> values, Operator op) {
        double res = op == Operator.MIN ? Double.MAX_VALUE : Double.MIN_VALUE;
        T obj = null;
        for (T o : values) {
            double d = toDouble(o);
            if ((op == Operator.MIN && d < res) || (op == Operator.MAX && d > res)) {
                res = d;
                obj = o;
            }
        }
        return obj;
    }

    public List<Integer> asList(int[] values) {
        List<Integer> result = new LinkedList<Integer>();
        for (int v : values) {
            result.add(v);
        }
        return result;
    }

    public List<Long> asList(long[] values) {
        List<Long> result = new LinkedList<Long>();
        for (long v : values) {
            result.add(v);
        }
        return result;
    }

    public List<Double> asList(double[] values) {
        List<Double> result = new LinkedList<Double>();
        for (double v : values) {
            result.add(v);
        }
        return result;
    }

    public Double stdDev(List<?> values) {
        return stdDev(toArray(values));
    }

    public Double stdDev(double[] values) {
        return Math.sqrt(StatUtils.variance(values));
    }

    public Double toDouble(Object o) {
        if (o instanceof Double) {
            return (Double) o;
        }
        if (o instanceof Integer) {
            return (double) (int) (Integer) o;
        }
        if (o instanceof Long) {
            return (double) (long) (Long) o;
        }
        return Double.parseDouble("" + o);
    }

    public List<Double> toDoubles(List<?> values) {
        List<Double> d = new LinkedList<Double>();
        for (Object o : values) {
            double val = toDouble(o);
            d.add(val);
        }
        return d;
    }

    public double[] toArray(List<?> values) {
        double[] d = new double[values.size()];
        int count = 0;
        for (Object o : values) {
            double val = o instanceof Double ? (Double) o : Double.parseDouble("" + o);
            d[count++] = val;
        }
        return d;
    }

    public Integer toInteger(String in) {
        if (in == null) {
            return null;
        }
        try {
            return Integer.parseInt(in);
        } catch (Exception e) {
            return null;
        }
    }

    public static MathUtil getInstance() {
//        System.out.println(Thread.currentThread().toString() + "-" + Thread.currentThread().getId() + " - " +
//                "MathUtil.getInstance()");
        return instance;
    }

    private static void assertion(boolean value) {
        if (!value) {
            throw new RuntimeException("Assertion failed.");
        }
    }

    public static void main(String[] args) {
        MathUtil m = new MathUtil();

        Integer d0 = m.getOutlier(Arrays.asList(1, 2), 0.95);
        assertion(d0 == null);

        Integer d1 = m.getOutlier(Arrays.asList(1, 2, 3, 8, 4, 10, 20), 0.95);
        assertion(d1 == null);

        Integer d2 = m.getOutlier(Arrays.asList(1, 2, 3, 8, 4, 10, 50), 0.95);
        assertion(d2 != null);

        Integer d3 = m.getOutlier(Arrays.asList(1, 2, 3, 8, 4, 10, 100), 0.95);
        assertion(d3 != null);

        Object d4 = m.getOutlier(Arrays.asList(), 0.95);
        assertion(d4 == null);

        Integer d5 = m.getOutlier(Arrays.asList(1, 2, 3, 8, 4, 10, 100), 0.9999999);
        assertion(d5 == null);

        Integer d6 = m.getOutlier(Arrays.asList(1, 2, 3, 8, 4, 10, 100), 0.999);
        assertion(d6 != null);

    }

}
