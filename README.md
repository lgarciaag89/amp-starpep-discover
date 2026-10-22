# AMP StarPep Discover

Java 21 application for training Weka classifiers with StarPep descriptors and
predicting peptides from FASTA files.

## Requirements

- JDK 21.
- Maven 3.9 or compatible.
- Access to the `tomocomd:starpep-md` package on GitHub Packages.

To authenticate Maven with GitHub Packages, configure `~/.m2/settings.xml`
with a token that has package read permission. For example:

```xml
<settings>
  <servers>
    <server>
      <id>startpep-md-github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

The server ID must match the GitHub Packages repository ID in `pom.xml`.
Set `GITHUB_ACTOR` and `GITHUB_TOKEN` in your local environment; do not store
the token in the repository.

## Build and package

```shell
mvn clean package
```

The standalone application JAR is created at
`target/amp-starpep-discover.jar`. It includes the runtime
dependencies and requires only Java 21 or later on the machine where it runs.
The application has two entry points: `tomocomd.BuildModel` and
`tomocomd.PredictPeptidesCli`.

In PowerShell, set the classpath as follows:

```powershell
$appClasspath = "target\amp-starpep-discover.jar"
```

The `scripts/` directory also contains launchers that use the standalone JAR
and check that Java 21 or later is available. Run `mvn clean package` before
using them. On Windows, use the `.bat` files; on macOS/Linux or Git Bash, use
the `.sh` files. Arguments are passed directly to the CLI. To run the JAR
directly, specify the entry-point class with `-cp`, for example:

```shell
java -cp target/amp-starpep-discover.jar \
  tomocomd.PredictPeptidesCli --help
```

## GitHub releases

Every push to `main`, including a merge, triggers the release workflow. It
increments the patch number from the latest `v<version>` tag (or from the
version in `pom.xml` for the first release), builds the standalone JAR, creates
a new `v<version>` tag, and publishes a GitHub Release with the versioned JAR
attached. The version in `pom.xml` on `main` is not changed. The workflow
authenticates to GitHub Packages using the built-in `GITHUB_TOKEN`. Make sure
the workflow repository has read access to the `starpep-md` package in its
GitHub Packages settings.

## Train a model from FASTA

The FASTA file must contain one peptide per record. The endpoint file is a CSV
with one target-value column, in the same order as the FASTA records. The
descriptor file is UTF-8 text with one descriptor name per line and no header.

```powershell
.\scripts\train.bat `
  --model rf `
  --fasta-train data\training.fasta `
  --endpoint-file data\endpoint.csv `
  --descriptors data\md.txt
```

```shell
sh scripts/train.sh \
  --model rf \
  --fasta-train data/training.fasta \
  --endpoint-file data/endpoint.csv \
  --descriptors data/md.txt
```

By default, the endpoint is the first CSV column. To select another column by
name, use `--endpoint-name`. You can also train directly from a data CSV with
headers:

```powershell
java -cp $appClasspath tomocomd.BuildModel `
  --model rf `
  --data-train data\training.csv `
  --endpoint-name CLASS
```

The serialized model includes the classifier and attribute schema. Check the
training log for the path of the generated file.

The `--model` argument selects the algorithm configured in
`tomocomd.classifiers.BuildClassifier`. Supported algorithms are
`RandomForest` (`rf`), `RandomCommittee` (`rc`), `IBk` (`knn`), `BayesNet`
(`bn`), `SMO-PUK` (`smo`), `SMO-Poly`, `LinearRegression` (`lr`),
`SMOreg-PUK` (`svm`), `SMOreg-Poly`, `AdditiveRegressionRF` (`ar-rf`),
`AdditiveRegressionKnn` (`ar-knn`), `AdditiveRegressionSMOReg` (`ar-smoreg`),
`LogitBoost` (`lb`), and `AdaBoostM1` (`adaboost`). Use a nominal endpoint
for classification and a numeric endpoint for regression.

## Predict from FASTA

Prediction calculates the descriptors required by the model and creates a CSV
with the peptide identifier, descriptors, and prediction:

```powershell
$model = "data\training.fasta_rf.model"
.\scripts\predict.bat `
  --model $model `
  --fasta data\unknown-peptides.fasta `
  --output data\predictions.csv
```

```shell
MODEL="data/training.fasta_rf.model"
sh scripts/predict.sh \
  --model "$MODEL" \
  --fasta data/unknown-peptides.fasta \
  --output data/predictions.csv
```

If `--output` is omitted, `<fasta-name>_predictions.csv` is created next to the
FASTA file. An existing output CSV is replaced.
