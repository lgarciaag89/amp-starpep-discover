package tomocomd;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public final class Model implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AbstractClassifier classifier;
    private final Instances header;

    public Model(Classifier classifier, Instances trainingData)
            throws Exception {
        Objects.requireNonNull(classifier, "Classifier must not be null");
        Objects.requireNonNull(trainingData, "Training data must not be null");

        if (!(classifier instanceof AbstractClassifier)) {
            throw new IllegalArgumentException(
                    "Classifier must extend AbstractClassifier");
        }

        this.classifier = (AbstractClassifier) classifier;
        this.header = new Instances(trainingData, 0);
        validate();
    }

    public Classifier getClassifier() {
        return classifier;
    }

    public Instances getHeader() {
        return new Instances(header, 0);
    }

    void validate() throws Exception {
        validateRequiredFields();
        validateClassAttribute();
        validateAttributeNames();
        validateCapabilities();
    }

    private void validateRequiredFields() {
        if (classifier == null || header == null) {
            throw new IllegalArgumentException(
                    "Model bundle must contain a classifier and schema");
        }
    }

    private void validateClassAttribute() {
        if (header.classIndex() < 0
                || header.classIndex() >= header.numAttributes()) {
            throw new IllegalArgumentException(
                    "Model schema must identify a valid class attribute");
        }

        Attribute classAttribute = header.classAttribute();
        if (!classAttribute.isNominal() && !classAttribute.isNumeric()) {
            throw new IllegalArgumentException(
                    "Model class attribute must be nominal or numeric");
        }
    }

    private void validateAttributeNames() {
        Set<String> attributeNames = new HashSet<>();

        for (int index = 0; index < header.numAttributes(); index++) {
            String name = header.attribute(index).name();

            if (!attributeNames.add(name)) {
                throw new IllegalArgumentException(
                        "Model schema contains duplicate attribute: " + name);
            }

            if (index != header.classIndex()
                    && StarPepDescriptorExecutor.PEPTIDE_ID_ATTRIBUTE
                    .equals(name)) {
                throw new IllegalArgumentException(
                        "Model schema uses the reserved peptide ID attribute "
                                + "as a training feature");
            }
        }
    }

    private void validateCapabilities() throws Exception {
        Instances capabilityProbe = new Instances(header, 1);
        double[] values = new double[header.numAttributes()];
        Arrays.fill(values, Double.NaN);

        for (int index = 0; index < header.numAttributes(); index++) {
            values[index] = createProbeValue(header.attribute(index));
        }

        capabilityProbe.add(new DenseInstance(1.0, values));
        classifier.getCapabilities().testWithFail(capabilityProbe);
    }

    private double createProbeValue(Attribute attribute) {
        if (attribute.isNumeric() || attribute.isDate()) {
            return 0.0;
        }

        if (attribute.isNominal() && attribute.numValues() > 0) {
            return 0.0;
        }

        if (attribute.isString()) {
            return attribute.addStringValue("schema-validation");
        }

        throw new IllegalArgumentException(
                "Unsupported model schema attribute: " + attribute.name());
    }
}