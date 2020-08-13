/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.util;

import java.util.ArrayList;
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

    public boolean hasOutlier(final Object... values) {
        return hasOutlier(Arrays.asList(values));
    }

    public boolean hasOutlier(final List<?> values) {
        return getOutlier(values) != null;
    }

    public boolean hasOutlier(final List<?> values, final double significanceLevel) {
        return getOutlier(values, significanceLevel) != null;
    }

    /**
     * Returns a statistical outlier with the default significance level (0.95), or
     * null if no such outlier exists..
     */
    public <T> T getOutlier(final List<T> values) {
        return getOutlier(values, DEFAULT_SIGNIFICANCE_LEVEL);
    }

    public <T> T getOutlier(final List<T> values, final double significanceLevel) {
        final T result;
        final AtomicReference<T> outlier = new AtomicReference<>();
        final double grubbs = getGrubbsTestStatistic(values, outlier);
        final double size = values.size();
        if (size < 3) {
            result = null;
        } else {
            final TDistributionImpl t = new TDistributionImpl(size - 2.0);
            try {
                final double criticalValue = t.inverseCumulativeProbability((1.0 - significanceLevel) / (2.0 * size));
                final double criticalValueSquare = criticalValue * criticalValue;
                final double grubbsCompareValue = ((size - 1) / Math.sqrt(size))
                        * Math.sqrt((criticalValueSquare) / (size - 2.0 + criticalValueSquare));
                // System.out.println("critical value: " + grubbs + " - " + grubbsCompareValue);
                if (grubbs > grubbsCompareValue) {
                    result = outlier.get();
                } else {
                    result = null;
                }
            } catch (final MathException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    /**
     * returns a minimum outlier (if one exists)
     */
    public <T> T getOutlierMin(final List<T> values) {
        T result = getOutlier(values, DEFAULT_SIGNIFICANCE_LEVEL);
        if (result != null) {
            final double d1 = toDouble(result).doubleValue();
            final double d2 = toDouble(min(values)).doubleValue();
            if (d1 != d2) {
                result = null;
            }
        }
        return result;
    }

    /**
     * returns a minimum outlier (if one exists)
     */
    public <T> T getOutlierMax(final List<T> values) {
        T result = getOutlier(values, DEFAULT_SIGNIFICANCE_LEVEL);
        if (result != null) {
            final double d1 = toDouble(result).doubleValue();
            final double d2 = toDouble(max(values)).doubleValue();
            if (d1 != d2) {
                result = null;
            }
        }
        return result;
    }

    public <T> double getGrubbsTestStatistic(final List<T> values, final AtomicReference<T> outlier) {
        final double[] array = toArray(values);
        final double mean = StatUtils.mean(array);
        final double stddev = stdDev(values).doubleValue();
        double maxDev = 0;
        for (final T o : values) {
            final double d = toDouble(o).doubleValue();
            if (Math.abs(mean - d) > maxDev) {
                maxDev = Math.abs(mean - d);
                outlier.set(o);
            }
        }
        final double grubbs = maxDev / stddev;
        // System.out.println("mean/stddev/maxDev/grubbs: " + mean + " - " + stddev + "
        // - " + maxDev + " - " + grubbs);
        return grubbs;
    }

    private enum Operator {
        MIN, MAX
    }

    public double sum(final Collection<?> c) {
        double s = 0;
        for (final Object o : c) {
            s += Double.parseDouble("" + o);
        }
        return s;
    }

    public double average(final Collection<?> c) {
        return sum(c) / c.size();
    }

    public double avg(final Collection<?> c) {
        return average(c);
    }

    public <T> T min(final List<T> values) {
        return executeOp(values, Operator.MIN);
    }

    public <T> T max(final List<T> values) {
        return executeOp(values, Operator.MAX);
    }

    public double max(final double[] values) {
        return executeOp(asList(values), Operator.MAX).doubleValue();
    }

    public int max(final int[] values) {
        return executeOp(asList(values), Operator.MAX).intValue();
    }

    public long max(final long[] values) {
        return executeOp(asList(values), Operator.MAX).longValue();
    }

    private <T> T executeOp(final List<T> values, final Operator op) {
        double res = op == Operator.MIN ? Double.MAX_VALUE : Double.MIN_VALUE;
        T obj = null;
        for (final T o : values) {
            final double d = toDouble(o).doubleValue();
            if ((op == Operator.MIN && d < res) || (op == Operator.MAX && d > res)) {
                res = d;
                obj = o;
            }
        }
        return obj;
    }

    public List<Integer> asList(final int... values) {
        final List<Integer> result = new ArrayList<>(values.length);
        for (final int v : values) {
            result.add(Integer.valueOf(v));
        }
        return result;
    }

    public List<Long> asList(final long... values) {
        final List<Long> result = new ArrayList<>(values.length);
        for (final long v : values) {
            result.add(Long.valueOf(v));
        }
        return result;
    }

    public List<Double> asList(final double... values) {
        final List<Double> result = new ArrayList<>(values.length);
        for (final double v : values) {
            result.add(Double.valueOf(v));
        }
        return result;
    }

    public Double stdDev(final List<?> values) {
        return stdDev(toArray(values));
    }

    public Double stdDev(final double[] values) {
        return Double.valueOf(Math.sqrt(StatUtils.variance(values)));
    }

    public Double toDouble(final Object o) {
        if (o instanceof Double) {
            return (Double) o;
        }
        if (o instanceof Integer) {
            return Double.valueOf(((Integer) o).intValue());
        }
        if (o instanceof Long) {
            return Double.valueOf(((Long) o).longValue());
        }
        return Double.valueOf(String.valueOf(o));
    }

    public List<Double> toDoubles(final List<?> values) {
        final List<Double> d = new LinkedList<>();
        for (final Object o : values) {
            final Double val = toDouble(o);
            d.add(val);
        }
        return d;
    }

    public double[] toArray(final List<?> values) {
        final double[] result = new double[values.size()];
        int count = 0;
        for (final Object o : values) {
            final Double d = o instanceof Double ? (Double) o : Double.valueOf(String.valueOf(o));
            result[count++] = d.doubleValue();
        }
        return result;
    }

    public Integer toInteger(final String in) {
        if (in == null) {
            return null;
        }
        try {
            return Integer.valueOf(in);
        } catch (@SuppressWarnings("unused") final Exception e) {
            return null;
        }
    }

    public static MathUtil getInstance() {
//        System.out.println(Thread.currentThread().toString() + "-" + Thread.currentThread().getId() + " - " +
//                "MathUtil.getInstance()");
        return instance;
    }

    private static void assertion(final boolean value) {
        if (!value) {
            throw new RuntimeException("Assertion failed.");
        }
    }

    public static void main(final String[] args) {
        final MathUtil m = new MathUtil();

        final Integer d0 = m.getOutlier(m.asList(1, 2), 0.95);
        assertion(d0 == null);

        final Integer d1 = m.getOutlier(m.asList(1, 2, 3, 8, 4, 10, 20), 0.95);
        assertion(d1 == null);

        final Integer d2 = m.getOutlier(m.asList(1, 2, 3, 8, 4, 10, 50), 0.95);
        assertion(d2 != null);

        final Integer d3 = m.getOutlier(m.asList(1, 2, 3, 8, 4, 10, 100), 0.95);
        assertion(d3 != null);

        final Object d4 = m.getOutlier(m.asList(), 0.95);
        assertion(d4 == null);

        final Integer d5 = m.getOutlier(m.asList(1, 2, 3, 8, 4, 10, 100), 0.9999999);
        assertion(d5 == null);

        final Integer d6 = m.getOutlier(m.asList(1, 2, 3, 8, 4, 10, 100), 0.999);
        assertion(d6 != null);

    }

}
