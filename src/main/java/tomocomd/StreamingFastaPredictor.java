package tomocomd;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import tomocomd.io.ReadPeptideFile;
import tomocomd.model.PeptideContainer;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.SerializationHelper;

/**
 * Block-wise FASTA prediction for very large inputs. Peptides are read,
 * processed and written {@code blockSize} at a time, so memory stays bounded
 * by the block size instead of the FASTA size. The CSV layout matches\n * {@code PredictPeptidesCli} (descriptors, Peptide_ID, Prediction).
 */
public final class StreamingFastaPredictor {

    /** Receives progress after each processed block. */
    @FunctionalInterface
    public interface ProgressListener {
        /** @param fraction share of the FASTA file already read (0..1) */
        void onBlock(long peptides, double fraction);
    }

    static final String PREDICTION_LABEL = "Prediction";


    /** Classifier, training header, and ordered descriptor names. */
    record LoadedModel(
            Classifier model, Instances header, List<String> descriptors) {
    }

    private StreamingFastaPredictor() {
    }

    static LoadedModel loadModel(Path modelPath) throws Exception {
        Objects.requireNonNull(modelPath, "Model path must not be null");
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalArgumentException(
                    "Model file does not exist or is not a regular file: "
                            + modelPath);
        }
        if (!(SerializationHelper.read(modelPath.toString())
                instanceof Model trainedModel)) {
            throw new IllegalArgumentException(
                    "The file does not contain a StarPep model bundle: "
                            + modelPath);
        }
        Classifier model = trainedModel.getClassifier();
        Instances header = trainedModel.getHeader();

        List<String> descriptors = new ArrayList<>();
        for (int i = 0; i < header.numAttributes(); i++) {
            if (i == header.classIndex()) {
                continue;
            }
            Attribute attribute = header.attribute(i);
            if (!attribute.isNumeric()) {
                throw new IllegalArgumentException(
                        "Unsupported non-numeric descriptor: "
                                + attribute.name());
            }
            if (PREDICTION_LABEL.equals(attribute.name())
                    || StarPepDescriptorExecutor.PEPTIDE_ID_ATTRIBUTE
                            .equals(attribute.name())) {
                throw new IllegalArgumentException(
                        "Model schema uses reserved attribute name: "
                                + attribute.name());
            }
            descriptors.add(attribute.name());
        }
        if (descriptors.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one descriptor name is required");
        }
        return new LoadedModel(model, header, descriptors);
    }

    /** @return number of peptides written */
    public static long predict(
            Path modelPath,
            Path fastaFile,
            Path outputFile,
            int threads,
            int blockSize,
            ProgressListener progress) throws Exception {
        Objects.requireNonNull(fastaFile, "FASTA path must not be null");
        Objects.requireNonNull(outputFile, "Output path must not be null");
        if (blockSize < 1) {
            throw new IllegalArgumentException(
                    "Block size must be at least 1");
        }

        LoadedModel loaded = loadModel(modelPath);
        PeptidePredictionScheduler scheduler = new PeptidePredictionScheduler(
                loaded.model(), loaded.header(), loaded.descriptors(),
                threads);

        Path absoluteOutput = outputFile.toAbsolutePath();
        if (absoluteOutput.getParent() != null) {
            Files.createDirectories(absoluteOutput.getParent());
        }
        return predict(loaded, fastaFile, absoluteOutput, scheduler, blockSize, progress);
    }

    private static long predict(
            LoadedModel loaded,
            Path fastaFile,
            Path absoluteOutput,
            PeptidePredictionScheduler scheduler,
            int blockSize,
            ProgressListener progress) throws IOException {

        long totalBytes = Math.max(1, Files.size(fastaFile));
        long readBytes = 0;
        long written = 0;
        try (BufferedReader reader = Files.newBufferedReader(
                        fastaFile, StandardCharsets.UTF_8);
                BufferedWriter writer = Files.newBufferedWriter(
                        absoluteOutput,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            writeHeader(writer, loaded.descriptors());

            StringBuilder block = new StringBuilder();
            List<String> ids = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                readBytes += line.length() + 1L;
                if (line.startsWith(">")) {
                    if (ids.size() == blockSize) {
                        written += processBlock(
                                block, ids, scheduler, loaded, writer);
                        block.setLength(0);
                        ids.clear();
                        if (progress != null) {
                            progress.onBlock(written, Math.min(1.0,
                                    (double) readBytes / totalBytes));
                        }
                    }
                    String id = line.substring(1).trim();
                    if (id.isEmpty()) {
                        throw new IllegalArgumentException(
                                "FASTA contains an empty peptide ID");
                    }
                    ids.add(id);
                }
                block.append(line).append('\n');
            }
            if (!ids.isEmpty()) {
                written += processBlock(
                        block, ids, scheduler, loaded, writer);
            }
        }
        if (written == 0) {
            throw new IllegalArgumentException(
                    "At least one peptide is required");
        }
        return written;
    }

    private static int processBlock(
            StringBuilder block,
            List<String> ids,
            PeptidePredictionScheduler scheduler,
            LoadedModel loaded,
            BufferedWriter writer) throws IOException {
        PeptideContainer peptides = ReadPeptideFile.readPeptideFile(
                new ByteArrayInputStream(
                        block.toString().getBytes(StandardCharsets.UTF_8)));
        if (peptides.size() != ids.size()) {
            throw new IllegalArgumentException(
                    "FASTA peptide ID count does not match peptide count: "
                            + ids.size() + " vs " + peptides.size());
        }

        PeptidePredictionScheduler.Result result = scheduler.run(peptides);
        for (int i = 0; i < ids.size(); i++) {
            for (double value : result.descriptors()[i]) {
                writer.write(Double.isNaN(value)
                        ? "?"
                        : Double.toString(value));
                writer.write(',');
            }
            writer.write(csvField(ids.get(i)));
            writer.write(',');
            writer.write(csvField(formatPrediction(
                    result.predictions()[i],
                    loaded.header().classAttribute())));
            writer.newLine();
        }
        return ids.size();
    }

    private static void writeHeader(
            BufferedWriter writer, List<String> descriptors)
            throws IOException {
        for (String name : descriptors) {
            writer.write(csvField(name));
            writer.write(',');
        }
        writer.write(StarPepDescriptorExecutor.PEPTIDE_ID_ATTRIBUTE);
        writer.write(',');
        writer.write(PREDICTION_LABEL);
        writer.newLine();
    }

    /** Same quoting and spreadsheet-formula protection as CSVManage. */
    private static String csvField(String value) {
        String safe = CSVManage.startsWithFormula(value) ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"")
                || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    static String formatPrediction(
            double prediction,
            Attribute classAttribute) {
        if (Double.isNaN(prediction)) {
            return "?";
        }
        if (!classAttribute.isNominal()) {
            return Double.toString(prediction);
        }
        int classIndex = (int) prediction;
        if (prediction != classIndex
                || classIndex < 0
                || classIndex >= classAttribute.numValues()) {
            throw new IllegalStateException(
                    "Classifier returned an invalid nominal class index: "
                            + prediction);
        }
        return classAttribute.value(classIndex);
    }
}
