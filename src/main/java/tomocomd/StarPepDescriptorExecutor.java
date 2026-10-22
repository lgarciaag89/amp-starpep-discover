package tomocomd;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.math3.linear.RealMatrix;
import tomocomd.io.ReadPeptideFile;
import tomocomd.md.ComputeBatch;
import tomocomd.model.PeptideContainer;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class StarPepDescriptorExecutor {

    public static final String PEPTIDE_ID_ATTRIBUTE = "Peptide_ID";

    /**
     * Calculates the requested StarPep descriptors for peptides in a FASTA
     * file using the descriptor names listed in a text file.
     */
    public Instances compute(Path fastaFile, Path descriptorFile)
            throws IOException {
        Objects.requireNonNull(fastaFile, "FASTA file must not be null");
        Objects.requireNonNull(
                descriptorFile, "Descriptor file must not be null");

        PeptideContainer peptides =
                ReadPeptideFile.readPeptideFile(fastaFile.toString());
        return compute(
                loadDescriptors(descriptorFile),
                peptides,
                fastaFile.getFileName().toString());
    }

    /**
     * Calculates descriptors from FASTA and carries each FASTA identifier in
     * the corresponding result row.
     */
    public Instances computeWithIds(Path fastaFile, Path descriptorFile)
            throws IOException {
        Objects.requireNonNull(fastaFile, "FASTA file must not be null");
        Objects.requireNonNull(
                descriptorFile, "Descriptor file must not be null");

        PeptideContainer peptides =
                ReadPeptideFile.readPeptideFile(fastaFile.toString());
        return computeWithIds(
                fastaFile,
                loadDescriptors(descriptorFile),
                peptides,
                fastaFile.getFileName().toString());
    }

    public Instances computeWithIds(
            Path fastaFile,
            Collection<String> descriptors,
            PeptideContainer peptides,
            String nameData) throws IOException {
        Objects.requireNonNull(fastaFile, "FASTA file must not be null");
        return compute(
                descriptors,
                peptides,
                nameData,
                readPeptideIds(fastaFile));
    }

    private List<String> readPeptideIds(Path fastaFile) throws IOException {
        List<String> peptideIds = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(
                fastaFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    String id = line.substring(1).trim();
                    if (id.isEmpty()) {
                        throw new IllegalArgumentException(
                                "FASTA contains an empty peptide ID");
                    }
                    peptideIds.add(id);
                }
            }
        }
        return peptideIds;
    }

    public Instances compute(
            Set<String> pdSet,
            PeptideContainer peptides,
            String nameData) {
        return compute((Collection<String>) pdSet, peptides, nameData);
    }

    /**
     * Calculates StarPep descriptors for the supplied peptides and returns
     * them as Weka instances. Descriptor and instance order follows the input.
     */
    public Instances compute(
            Collection<String> descriptors,
            PeptideContainer peptides,
            String nameData) {
        return compute(descriptors, peptides, nameData, null);
    }

    private Instances compute(
            Collection<String> descriptors,
            PeptideContainer peptides,
            String nameData,
            List<String> peptideIds) {
        Objects.requireNonNull(descriptors, "Descriptors must not be null");
        Objects.requireNonNull(peptides, "Peptides must not be null");

        List<String> descriptorNames = new ArrayList<>(descriptors);
        if (descriptorNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one descriptor name is required");
        }
        if (descriptorNames.stream().anyMatch(
                name -> name == null || name.trim().isEmpty())) {
            throw new IllegalArgumentException(
                    "Descriptor names must not be null or blank");
        }
        if (peptides.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one peptide is required");
        }
        if (peptideIds != null && peptideIds.size() != peptides.size()) {
            throw new IllegalArgumentException(
                    "FASTA peptide ID count does not match peptide count: "
                            + peptideIds.size() + " vs " + peptides.size());
        }
        if (peptideIds != null && descriptorNames.contains(
                PEPTIDE_ID_ATTRIBUTE)) {
            throw new IllegalArgumentException(
                    "Descriptor name conflicts with reserved ID attribute: "
                            + PEPTIDE_ID_ATTRIBUTE);
        }
        Objects.requireNonNull(nameData, "Dataset name must not be null");

        ComputeBatch computeBatch =
                new ComputeBatch(peptides.size(), descriptorNames.size());
        computeBatch.computeInBatch(peptides, descriptorNames);
        RealMatrix real = computeBatch.getResults();

        ArrayList<Attribute> atts = new ArrayList<>();
        descriptorNames.forEach(head -> atts.add(new Attribute(head)));
        if (peptideIds != null) {
            atts.add(new Attribute(PEPTIDE_ID_ATTRIBUTE, (List<String>) null));
        }

        Instances instances =
                new Instances(nameData, atts, peptides.size());

        for (int i = 0; i < peptides.size(); i++) {
            double[] descriptorValues = real.getRow(i);
            double[] values = peptideIds == null
                    ? descriptorValues
                    : new double[descriptorValues.length + 1];
            if (peptideIds != null) {
                System.arraycopy(
                        descriptorValues,
                        0,
                        values,
                        0,
                        descriptorValues.length);
                Attribute idAttribute =
                        instances.attribute(PEPTIDE_ID_ATTRIBUTE);
                values[descriptorValues.length] =
                        idAttribute.addStringValue(peptideIds.get(i));
            }
            instances.add(new DenseInstance(1, values));
        }
        return instances;
    }

    private Set<String> loadDescriptors(Path descriptorFile)
            throws IOException {
        Set<String> descriptors = new LinkedHashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(
                descriptorFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String descriptor = line.trim();
                if (!descriptor.isEmpty()) {
                    descriptors.add(descriptor);
                }
            }
        }

        if (descriptors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Descriptors file contains no descriptor names: "
                            + descriptorFile);
        }

        return descriptors;
    }
}
