package tomocomd;

import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class CSVManage {

    private static final String MSG_ACC = "Problems accessing to file %s";

    private CSVManage() {
    }

    public static String saveDescriptorMResult(
            Instances instances,
            String pathOut) throws AExOpDCSException {
        Objects.requireNonNull(instances, "Instances must not be null");
        Objects.requireNonNull(pathOut, "Output path must not be null");

        Path requestedPath = Path.of(pathOut);
        Path outputPath = availablePath(requestedPath);
        try {
            writeCsv(instances, outputPath);
        } catch (IOException exception) {
            throw AExOpDCSException.ExceptionType.CSV_FILE_WRITING_EXCEPTION
                    .get("Problems writing CSV file: " + outputPath, exception);
        }
        return outputPath.toAbsolutePath().toString();
    }

    public static void writeCsv(Instances instances, Path outputPath)
            throws IOException {
        Objects.requireNonNull(instances, "Instances must not be null");
        Objects.requireNonNull(outputPath, "Output path must not be null");

        Path absolutePath = outputPath.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                absolutePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            for (int column = 0; column < instances.numAttributes(); column++) {
                if (column > 0) {
                    writer.write(',');
                }
                writer.write(csvField(
                        instances.attribute(column).name(), true));
            }
            writer.newLine();

            for (Instance instance : instances) {
                for (int column = 0;
                        column < instances.numAttributes();
                        column++) {
                    if (column > 0) {
                        writer.write(',');
                    }
                    writer.write(csvField(
                            formatValue(instance, instances.attribute(column),
                                    column),
                            isTextAttribute(instances.attribute(column))));
                }
                writer.newLine();
            }
        }
    }

    static String csvField(String value, boolean protectFormula) {
        Objects.requireNonNull(value, "CSV value must not be null");

        String safeValue = protectFormula && startsWithFormula(value)
                ? "'" + value
                : value;
        if (safeValue.contains(",") || safeValue.contains("\"")
                || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    public static Instances loadCSV(String path) throws AExOpDCSException {
        Objects.requireNonNull(path, "CSV path must not be null");
        try {
            CSVLoader loader = new CSVLoader();
            loader.setFile(Path.of(path).toFile());
            loader.setFieldSeparator(",");
            return loader.getDataSet();
        } catch (IOException exception) {
            throw AExOpDCSException.ExceptionType.CSV_FILE_LOADING_EXCEPTION
                    .get(String.format(MSG_ACC, path), exception);
        }
    }

    private static Path availablePath(Path requestedPath) {
        if (!Files.exists(requestedPath)) {
            return requestedPath;
        }

        String fileName = requestedPath.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0
                ? fileName.substring(0, extensionIndex)
                : fileName;
        String extension = extensionIndex > 0
                ? fileName.substring(extensionIndex)
                : "";
        Path parent = requestedPath.getParent();

        for (int suffix = 1; ; suffix++) {
            Path candidate = (parent == null ? Path.of("") : parent)
                    .resolve(baseName + "_" + suffix + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    private static String formatValue(
            Instance instance,
            Attribute attribute,
            int index) {
        if (instance.isMissing(index)) {
            return "?";
        }
        if (attribute.isDate()) {
            return attribute.formatDate(instance.value(index));
        }
        if (attribute.isNominal() || attribute.isString()) {
            return instance.stringValue(index);
        }
        return Double.toString(instance.value(index));
    }

    private static boolean isTextAttribute(Attribute attribute) {
        return attribute.isNominal() || attribute.isString();
    }

    public static boolean startsWithFormula(String value) {
        int index = 0;
        while (index < value.length()) {
            char character = value.charAt(index);
            if (character == '\uFEFF'
                    || Character.isWhitespace(character)
                    || Character.isISOControl(character)) {
                index++;
            } else {
                break;
            }
        }
        if (index == value.length()) {
            return false;
        }

        char firstMeaningfulCharacter = value.charAt(index);
        return firstMeaningfulCharacter == '='
                || firstMeaningfulCharacter == '+'
                || firstMeaningfulCharacter == '-'
                || firstMeaningfulCharacter == '@';
    }
}
