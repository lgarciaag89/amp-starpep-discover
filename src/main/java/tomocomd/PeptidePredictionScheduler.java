package tomocomd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import tomocomd.md.ComputeMD;
import tomocomd.model.Peptide;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Computes descriptors and predicts peptides using a fork/join pool.
 */
public final class PeptidePredictionScheduler {

    /**
     * Descriptor values and prediction for each peptide, in input order.
     */
    public record Result(double[][] descriptors, double[] predictions) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof Result(double[][] otherDescriptors, double[] otherPredictions))) {
                return false;
            }

            return Arrays.deepEquals(descriptors, otherDescriptors)
                    && Arrays.equals(predictions, otherPredictions);
        }

        @Override
        public int hashCode() {
            int result = Arrays.deepHashCode(descriptors);
            result = 31 * result + Arrays.hashCode(predictions);
            return result;
        }

        @Override
        public String toString() {
            return "Result[descriptors=" + Arrays.deepToString(descriptors)
                    + ", predictions=" + Arrays.toString(predictions)
                    + "]";
        }
    }

    private final Classifier model;
    private final Instances header;
    private final List<String> descriptors;
    private final int[] attributeIndexes;
    private final int threads;

    public PeptidePredictionScheduler(
            Classifier model,
            Instances header,
            List<String> descriptors,
            int threads) {
        this.model = Objects.requireNonNull(model);
        this.header = Objects.requireNonNull(header);
        this.descriptors = List.copyOf(descriptors);
        this.threads = Math.max(1, threads);
        this.attributeIndexes = new int[this.descriptors.size()];
        for (int i = 0; i < attributeIndexes.length; i++) {
            var attribute = header.attribute(this.descriptors.get(i));
            if (attribute == null) {
                throw new IllegalArgumentException(
                        "Model header does not contain descriptor: "
                                + this.descriptors.get(i));
            }
            attributeIndexes[i] = attribute.index();
        }
    }

    public Result run(List<Peptide> input) {
        // PeptideContainer is a LinkedList; indexed access must be O(1).
        List<Peptide> peptides = new ArrayList<>(input);
        int count = peptides.size();
        double[][] values = new double[count][];
        double[] predictions = new double[count];

        // Descriptor calculation is the parallel part. Copying the classifier
        // per thread is far costlier than the prediction itself, so a single
        // instance is shared and classification is serialized.
        IntConsumer task = index -> {
            try {
                processOne(
                        peptides.get(index), index, values, predictions);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Failed to predict peptide "
                                + peptides.get(index).getIdPeptide(),
                        exception);
            }
        };
        schedule(peptides, task);
        return new Result(values, predictions);
    }

    private void schedule(List<Peptide> peptides, IntConsumer task) {
        try (ForkJoinPool pool = new ForkJoinPool(threads)) {
            await(pool.submit(() ->
                    IntStream.range(0, peptides.size())
                            .parallel()
                            .forEach(task)
            ));
        }
    }

    private void processOne(
            Peptide peptide,
            int index,
            double[][] values,
            double[] predictions) throws Exception {
        double[] row = new double[descriptors.size()];
        double[] instanceValues = new double[header.numAttributes()];
        Arrays.fill(instanceValues, Double.NaN);
        for (int d = 0; d < row.length; d++) {
            row[d] = ComputeMD.compute(peptide, descriptors.get(d));
            instanceValues[attributeIndexes[d]] = row[d];
        }
        Instance instance = new DenseInstance(1.0, instanceValues);
        instance.setDataset(header);
        values[index] = row;
        synchronized (model) {
            predictions[index] = model.classifyInstance(instance);
        }
    }

    /***
     * Awaits the completion of a future, re-throwing any exceptions as
     * AExOpDCSException.
     *
     * @param future the future to await
     * @throws AExOpDCSException if the future throws an exception
     */
    private static void await(Future<?> future) throws AExOpDCSException {
        try {
            future.get();

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw AExOpDCSException.ExceptionType.EXECUTION_ERROR.get(
                    "Interrupted while waiting for the task to finish",
                    exception
            );

        } catch (ExecutionException exception) {
            throw AExOpDCSException.ExceptionType.EXECUTION_ERROR.get(
                    "Error while executing the task",
                    exception.getCause() != null ? exception.getCause() : exception
            );
        }
    }
}

