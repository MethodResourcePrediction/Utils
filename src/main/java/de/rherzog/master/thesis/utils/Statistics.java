package de.rherzog.master.thesis.utils;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

// Stolen from: https://stackoverflow.com/a/7988556
public class Statistics {
	double[] data;
	int size;

	public Statistics(double[] data) {
		this.data = data;
		size = data.length;
	}

	public static Statistics of(IntStream intStream) {
		return Statistics.of(intStream.asDoubleStream());
	}

	public static Statistics of(DoubleStream doubleStream) {
		return Statistics.of(doubleStream.toArray());
	}

	public static Statistics of(LongStream longStream) {
		return Statistics.of(longStream.asDoubleStream());
//		return Statistics.of(DoubleStream.of(longStream.asDoubleStream().toArray()));
	}

	public static Statistics of(Stream<Long> stream) {
		return of(stream.mapToLong(e -> e));
	}

	private static Statistics of(double[] doubles) {
		double[] doubleArray = new double[doubles.length];
		for (int i = 0; i < doubles.length; i++) {
			doubleArray[i] = doubles[i];
		}
		return new Statistics(doubleArray);
	}

	public double getMean() {
		double sum = 0.0;
		for (double a : data)
			sum += a;
		return sum / size;
	}

	public double getVariance() {
		if (size == 1) {
			return 0;
		}
		double mean = getMean();
		double temp = 0;
		for (double a : data)
			temp += (a - mean) * (a - mean);
		return temp / (size - 1);
	}

	public double getStdDev() {
		return Math.sqrt(getVariance());
	}

	public double median() {
		Arrays.sort(data);
		if (data.length % 2 == 0)
			return (data[(data.length / 2) - 1] + data[data.length / 2]) / 2.0;
		return data[data.length / 2];
	}

	public double minimum() {
		double min = Double.MAX_VALUE;
		for (double d : data) {
			min = Math.min(min, d);
		}
		return min;
	}

	public double maximum() {
		double max = Double.MIN_VALUE;
		for (double d : data) {
			max = Math.max(max, d);
		}
		return max;
	}

	public double precision(Statistics statistics) {
		return 0d;
	}

	/**
	 * Generates a new random gaussian value based on the dataset.
	 * 
	 * @see https://stackoverflow.com/a/14657177
	 * @return next random gaussian
	 */
	public double randomGaussian() {
		return randomGaussian(getMean(), getStdDev());
	}

	public static double randomGaussian(double mean, double stdDev) {
		Random random = new Random();
		double value = stdDev * ((1 + random.nextGaussian()) / 2) + mean;
		return value;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Statistics for @" + Integer.toHexString(data.hashCode()) + ":\n");
		builder.append("  Mean: " + getMean() + "\n");
		builder.append("  StdDev: " + getStdDev() + "\n");
		builder.append("  Variance: " + getVariance() + "\n");
		return builder.toString();
	}

	public String toString(boolean printRandomGaussians) {
		if (!printRandomGaussians) {
			return toString();
		}
		StringBuilder builder = new StringBuilder(toString());
		builder.append("  Next 10 gaussians: " + "\n");
		for (int i = 0; i < 10; i++) {
			builder.append("    " + randomGaussian() + "\n");
		}
		return builder.toString();
	}

	public double[] getData() {
		return data;
	}
}
