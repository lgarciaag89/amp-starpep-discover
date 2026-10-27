package tomocomd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;

public final class ModelBuilder {

    private static final Logger LOGGER =
            LogManager.getLogger(ModelBuilder.class);

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
            case "randomcommittee", "rc" -> BuildClassifier.getRandomCommitteeRandomForest();
            case "ibk", "knn" -> BuildClassifier.getKnnCV();
            case "bayesnet", "bn" -> BuildClassifier.getBayesNet();
            case "smo", "smopuk", "smo-puk" -> BuildClassifier.getSMOPuk();
            case "smopoly", "smo-poly" -> BuildClassifier.getSMOPolyKernel();
            case "linearregression", "regression", "lr" -> BuildClassifier.getRegression();
            case "svm", "smoreg", "smoreg-puk" -> BuildClassifier.getSMORegPuk();
            case "smoreg-poly" -> BuildClassifier.getSMORegPolyKernel();
            case "additiveregressionrf", "ar-rf" -> BuildClassifier.getAdditiveRegressionRF();
            case "additiveregressionknn", "ar-knn" -> BuildClassifier.getAdditiveRegressionKnn();
            case "additiveregressionsmoreg", "ar-smoreg" -> BuildClassifier.getAdditiveRegressionSMOReg();
            case "logitboost", "lb" -> BuildClassifier.getLogitBoost();
            case "adaboostm1", "adaboost" -> BuildClassifier.getAdaBoostM1();
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
}
