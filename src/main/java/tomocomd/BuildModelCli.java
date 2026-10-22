package tomocomd;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import weka.core.Instances;

import java.io.IOException;
import java.nio.file.Path;

public final class BuildModelCli {

    private static final Logger LOGGER =
            LogManager.getLogger(BuildModelCli.class);

    private BuildModelCli() {
    }

    public static void buildModelAndSave(CommandLine commandLine)
            throws Exception {
        Instances trainingData = readTrainingData(commandLine);
        String modelName = commandLine.getOptionValue("m");

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "Training data loaded for model '{}': {} instances, "
                            + "{} attributes, endpoint '{}'",
                    modelName,
                    trainingData.numInstances(),
                    trainingData.numAttributes(),
                    trainingData.classAttribute().name());

            ModelBuilder.buildAndSaveModel(modelName, trainingData);
        }
    }

    public static void validateCommands(CommandLine commandLine) {
        String modelName = commandLine.getOptionValue("m");
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing model name. Provide it using -m");
        }

        boolean hasFasta = commandLine.hasOption("f");
        boolean hasCsv = commandLine.hasOption("d");
        if (hasFasta == hasCsv) {
            throw new IllegalArgumentException(
                    "Provide exactly one training input: -f FASTA or -d CSV");
        }
        if (hasFasta && !commandLine.hasOption("e")) {
            throw new IllegalArgumentException(
                    "Missing endpoints file. Provide it using -e");
        }
        if (hasFasta && !commandLine.hasOption("l")) {
            throw new IllegalArgumentException(
                    "Missing descriptors file. Provide it using -l");
        }
        if (hasCsv && (commandLine.hasOption("e")
                || commandLine.hasOption("l"))) {
            throw new IllegalArgumentException(
                    "Options -e and -l can only be used with -f");
        }
    }

    public static Options getOptions() {
        Options options = new Options();
        options.addOption(
                "m", "model", true,
                "Classifier name or alias (e.g. rf, rc, knn, bn, smo, lr)");
        options.addOption(
                "f", "fasta-train", true,
                "FASTA file with training sequences");
        options.addOption(
                "l", "descriptors", true,
                "File with one descriptor per line, without a header");
        options.addOption(
                "e", "endpoint-file", true,
                "CSV file containing the target property for each sequence");
        options.addOption(
                "d", "data-train", true,
                "CSV file with training data");
        options.addOption(
                "E", "endpoint-name", true,
                "Endpoint column name; defaults to the first column");
        options.addOption(
                "D", "debug", false,
                "Save application logs in the logs folder (not yet implemented)");
        options.addOption("h", "help", false, "Show help and exit");
        options.addOption("v", "version", false, "Show version and exit");
        return options;
    }

    private static Instances readTrainingData(CommandLine commandLine)
            throws IOException {
        if (commandLine.hasOption("d")) {
            Instances instances =
                    CSVManage.loadCSV(commandLine.getOptionValue("d"));
            validateDataset(instances, "Training CSV");
            setEndpoint(instances, commandLine);
            instances.setRelationName(commandLine.getOptionValue("d"));
            return instances;
        }

        Instances endpoints =
                CSVManage.loadCSV(commandLine.getOptionValue("e"));
        validateDataset(endpoints, "Endpoints CSV");
        setEndpoint(endpoints, commandLine);
        int endpointIndex = endpoints.classIndex();

        Instances descriptorInstances =
                new StarPepDescriptorExecutor().compute(
                        Path.of(commandLine.getOptionValue("f")),
                        Path.of(commandLine.getOptionValue("l")));
        validateDataset(descriptorInstances, "Calculated descriptors");

        if (endpoints.numInstances() != descriptorInstances.numInstances()) {
            throw new IllegalArgumentException(
                    "Endpoints and descriptors have different row counts: "
                            + endpoints.numInstances()
                            + " vs "
                            + descriptorInstances.numInstances());
        }

        Instances instances =
                Instances.mergeInstances(endpoints, descriptorInstances);
        instances.setRelationName(commandLine.getOptionValue("f"));
        CSVManage.saveDescriptorMResult(
                instances,
                instances.relationName() + "_descriptor_results.csv");
        instances.setClassIndex(endpointIndex);
        return instances;
    }

    private static void validateDataset(Instances instances, String source) {
        if (instances == null) {
            throw new IllegalArgumentException(
                    source + " could not be loaded");
        }
        if (instances.numAttributes() == 0) {
            throw new IllegalArgumentException(
                    source + " contains no columns");
        }
        if (instances.numInstances() == 0) {
            throw new IllegalArgumentException(
                    source + " contains no rows");
        }
    }

    private static void setEndpoint(
            Instances instances,
            CommandLine commandLine) {
        String endpointName = commandLine.getOptionValue("E");
        if (endpointName == null) {
            instances.setClassIndex(0);
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                        "Endpoint name not provided; using the first column: '{}'",
                        instances.classAttribute().name());
                return;
            }
        }

        if (instances.attribute(endpointName) == null) {
            throw new IllegalArgumentException(
                    "Endpoint column not found: " + endpointName);
        }
        instances.setClass(instances.attribute(endpointName));
    }
}
