package tomocomd;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public final class Model implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Classifier classifier;
    private final Instances header;

    public Model(Classifier classifier, Instances trainingData)
            throws Exception {
        this.classifier = Objects.requireNonNull(
                classifier, "Classifier must not be null");
        Objects.requireNonNull(trainingData, "Training data must not be null");
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
        if (classifier == null || header == null) {
            throw new IllegalArgumentException(
                    "Model bundle must contain a classifier and schema");
        }
        if (header.classIndex() < 0
                || header.classIndex() >= header.numAttributes()) {
            throw new IllegalArgumentException(
                    "Model schema must identify a valid class attribute");
        }

        var classAttribute = header.classAttribute();
        if (!classAttribute.isNominal() && !classAttribute.isNumeric()) {
            throw new IllegalArgumentException(
                    "Model class attribute must be nominal or numeric");
        }

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

        Instances capabilityProbe = new Instances(header, 1);
        double[] values = new double[header.numAttributes()];
        Arrays.fill(values, Double.NaN);
        for (int index = 0; index < header.numAttributes(); index++) {
            Attribute attribute = header.attribute(index);
            if (attribute.isNumeric() || attribute.isDate()) {
                values[index] = 0.0;
            } else if (attribute.isNominal() && attribute.numValues() > 0) {
                values[index] = 0.0;
            } else if (attribute.isString()) {
                values[index] = attribute.addStringValue("schema-validation");
            } else {
                throw new IllegalArgumentException(
                        "Unsupported model schema attribute: "
                                + attribute.name());
            }
        }
        capabilityProbe.add(new DenseInstance(1.0, values));
        classifier.getCapabilities().testWithFail(capabilityProbe);
    }
}
