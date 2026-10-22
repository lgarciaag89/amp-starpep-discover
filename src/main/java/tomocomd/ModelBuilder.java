package tomocomd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tomocomd.classifiers.BuildClassifier;
import tomocomd.io.ReadPeptideFile;
import tomocomd.model.PeptideContainer;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;

public final class ModelBuilder {

    private static final Logger LOGGER =
            LogManager.getLogger(ModelBuilder.class);
    private static final String PREDICTION_LABEL = "Prediction";

    private ModelBuilder() {
    }

    public static void buildAndSaveModel(
            String algorithmName,
            Instances dataset) throws Exception {
        Objects.requireNonNull(dataset, "Training dataset must not be null");
        if (dataset.classIndex() < 0) {
            throw new IllegalArgumentException(
                    "The class attribute must be set on the training dataset");
        }

        Classifier classifier = createClassifier(algorithmName);
        classifier.buildClassifier(dataset);

        Path modelPath = Path.of(
                dataset.relationName() + "_" + algorithmName + ".model");
        Path parent = modelPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        SerializationHelper.write(
                modelPath.toString(),
                new Model(classifier, dataset));
        LOGGER.info(
                "Model {} trained with {} instances and saved in {}",
                algorithmName,
                dataset.numInstances(),
                modelPath.toAbsolutePath());
    }

    public static Classifier createClassifier(String algorithmName) {
        String normalized =
                algorithmName == null ? "" : algorithmName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "The algorithm name cannot be empty.");
        }

        String key = normalized.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "randomforest", "rf" -> BuildClassifier.getRandomForest();
            case "randomcommittee", "rc" ->
                    BuildClassifier.getRandomCommitteeRandomForest();
            case "ibk", "knn" -> BuildClassifier.getKnnCV();
            case "bayesnet", "bn" -> BuildClassifier.getBayesNet();
            case "smo", "smopuk", "smo-puk" ->
                    BuildClassifier.getSMOPuk();
            case "smopoly", "smo-poly" ->
                    BuildClassifier.getSMOPolyKernel();
            case "linearregression", "regression", "lr" ->
                    BuildClassifier.getRegression();
            case "svm", "smoreg", "smoreg-puk" ->
                    BuildClassifier.getSMORegPuk();
            case "smoreg-poly" ->
                    BuildClassifier.getSMORegPolyKernel();
            case "additiveregressionrf", "ar-rf" ->
                    BuildClassifier.getAdditiveRegressionRF();
            case "additiveregressionknn", "ar-knn" ->
                    BuildClassifier.getAdditiveRegressionKnn();
            case "additiveregressionsmoreg", "ar-smoreg" ->
                    BuildClassifier.getAdditiveRegressionSMOReg();
            case "logitboost", "lb" -> BuildClassifier.getLogitBoost();
            case "adaboostm1", "adaboost" ->
                    BuildClassifier.getAdaBoostM1();
            default -> throw new IllegalArgumentException(
                    "Not supported algorithm: " + algorithmName
                            + ". Supported algorithms: RandomForest, "
                            + "RandomCommittee, IBk, BayesNet, SMO-PUK, "
                            + "SMO-Poly, LinearRegression, SMOreg-PUK, "
                            + "SMOreg-Poly, AdditiveRegressionRF, "
                            + "AdditiveRegressionKnn, "
                            + "AdditiveRegressionSMOReg, LogitBoost, "
                            + "AdaBoostM1.");
        };
    }

    /**
     * Predicts the class value for each instance without modifying the dataset.
     * Nominal class predictions are returned as their Weka class indexes;
     * numeric class predictions are returned as numeric values.
     */
    public static double[] predict(
            Classifier model,
            Instances instances) throws Exception {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(instances, "Instances must not be null");
        if (instances.classIndex() < 0) {
            throw new IllegalArgumentException(
                    "The class attribute must be set on the instances");
        }

        double[] predictions = new double[instances.numInstances()];
        for (int i = 0; i < instances.numInstances(); i++) {
            Instance instance = instances.instance(i);
            predictions[i] = model.classifyInstance(instance);
        }
        return predictions;
    }

    /**
     * Predicts a FASTA file and returns one row per prediction with its
     * peptide ID, keeping the identifier attached to its descriptor row.
     */
    public static Instances predictFastaWithIds(
            Path modelPath,
            Path fastaFile) throws Exception {
        Objects.requireNonNull(modelPath, "Model path must not be null");
        Objects.requireNonNull(fastaFile, "FASTA path must not be null");

        Model trainedModel = loadModel(modelPath);
        Classifier model = trainedModel.getClassifier();
        Instances header = trainedModel.getHeader();

        ArrayList<String> descriptors = new ArrayList<>();
        for (int i = 0; i < header.numAttributes(); i++) {
            if (i != header.classIndex()) {
                if (!header.attribute(i).isNumeric()) {
                    throw new IllegalArgumentException(
                            "Unsupported non-numeric descriptor: "
                                    + header.attribute(i).name());
                }
                if (PREDICTION_LABEL.equals(header.attribute(i).name())) {
                    throw new IllegalArgumentException(
                            "Model schema uses reserved attribute name: "
                                    + PREDICTION_LABEL);
                }
                descriptors.add(header.attribute(i).name());
            }
        }

        PeptideContainer peptides =
                ReadPeptideFile.readPeptideFile(fastaFile.toString());
        Instances calculatedDescriptors =
                new StarPepDescriptorExecutor().computeWithIds(
                        fastaFile,
                        descriptors,
                        peptides,
                        fastaFile.getFileName().toString());

        validateCalculatedDescriptorSchema(
                calculatedDescriptors, descriptors);
        Instances predictionInstances =
                createPredictionInstances(header, calculatedDescriptors);
        double[] predictions = predict(model, predictionInstances);
        Instances output = new Instances(
                calculatedDescriptors, predictions.length);
        output.insertAttributeAt(
                new Attribute(PREDICTION_LABEL, (List<String>) null),
                output.numAttributes());
        int predictionAttributeIndex = output.numAttributes() - 1;
        for (int i = 0; i < predictions.length; i++) {
            String prediction = formatPrediction(
                    predictions[i], header.classAttribute());

            double[] values = Arrays.copyOf(
                    calculatedDescriptors.instance(i).toDoubleArray(),
                    output.numAttributes());
            values[predictionAttributeIndex] =
                    output.attribute(predictionAttributeIndex)
                            .addStringValue(prediction);
            output.add(new DenseInstance(1.0, values));
        }
        return output;
    }

    private static Model loadModel(Path modelPath) throws Exception {
        Objects.requireNonNull(modelPath, "Model path must not be null");
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalArgumentException(
                    "Model file does not exist or is not a regular file: "
                            + modelPath);
        }

        Object loadedObject = SerializationHelper.read(modelPath.toString());
        if (!(loadedObject instanceof Model model)) {
            throw new IllegalArgumentException(
                    "The file does not contain a StarPep model bundle: "
                            + modelPath);
        }

        model.validate();
        return model;
    }

    private static void validateCalculatedDescriptorSchema(
            Instances calculatedDescriptors,
            List<String> expectedDescriptors) {
        Attribute peptideId =
                calculatedDescriptors.attribute(
                        StarPepDescriptorExecutor.PEPTIDE_ID_ATTRIBUTE);
        if (peptideId == null || !peptideId.isString()) {
            throw new IllegalArgumentException(
                    "Calculated data is missing the string peptide ID column");
        }
        if (calculatedDescriptors.numAttributes()
                != expectedDescriptors.size() + 1) {
            throw new IllegalArgumentException(
                    "Calculated descriptor columns do not match the model "
                            + "schema");
        }

        Set<String> expectedNames = new HashSet<>(expectedDescriptors);
        for (int i = 0; i < calculatedDescriptors.numAttributes(); i++) {
            Attribute attribute = calculatedDescriptors.attribute(i);
            if (!StarPepDescriptorExecutor.PEPTIDE_ID_ATTRIBUTE
                    .equals(attribute.name()) && (!attribute.isNumeric()
                        || !expectedNames.remove(attribute.name()))) {
                    throw new IllegalArgumentException(
                            "Unexpected or invalid calculated descriptor: "
                                    + attribute.name());
                }

        }
        if (!expectedNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "Calculated data is missing model descriptors: "
                            + String.join(", ", expectedNames));
        }
    }

    private static String formatPrediction(
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

    private static Instances createPredictionInstances(
            Instances header,
            Instances calculatedDescriptors) {
        Instances instances =
                new Instances(header, calculatedDescriptors.numInstances());
        for (int row = 0; row < calculatedDescriptors.numInstances(); row++) {
            double[] values = new double[header.numAttributes()];
            Arrays.fill(values, Double.NaN);

            for (int attributeIndex = 0;
                    attributeIndex < header.numAttributes();
                    attributeIndex++) {
                if (attributeIndex == header.classIndex()) {
                    continue;
                }

                String attributeName =
                        header.attribute(attributeIndex).name();
                if (StarPepDescriptorExecutor.PEPTIDE_ID_ATTRIBUTE
                        .equals(attributeName)) {
                    continue;
                }
                Attribute descriptor =
                        calculatedDescriptors.attribute(attributeName);
                if (descriptor == null) {
                    throw new IllegalArgumentException(
                            "Calculated descriptors do not contain model "
                                    + "attribute: " + attributeName);
                }
                values[attributeIndex] =
                        calculatedDescriptors.instance(row)
                                .value(descriptor.index());
            }

            instances.add(new DenseInstance(1.0, values));
        }
        return instances;
    }

}
