/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tomocomd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.bayes.BayesNet;
import weka.classifiers.bayes.net.estimate.BayesNetEstimator;
import weka.classifiers.bayes.net.estimate.SimpleEstimator;
import weka.classifiers.bayes.net.search.local.K2;
import weka.classifiers.bayes.net.search.local.LocalScoreSearchAlgorithm;
import weka.classifiers.bayes.net.search.local.Scoreable;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.SMOreg;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.classifiers.functions.supportVector.Puk;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.AdditiveRegression;
import weka.classifiers.meta.LogitBoost;
import weka.classifiers.meta.RandomCommittee;
import weka.classifiers.trees.RandomForest;
import weka.core.EuclideanDistance;
import weka.core.SelectedTag;
import weka.core.neighboursearch.LinearNNSearch;
import weka.core.neighboursearch.NearestNeighbourSearch;

/** @author potter */
public class BuildClassifier {

  private static final Logger logger = LogManager.getLogger(BuildClassifier.class);

  private BuildClassifier() {
    throw new IllegalStateException("BuildClassifier class");
  }

  public static AbstractClassifier getRandomForest() {
    RandomForest rf = new RandomForest();
    rf.setBagSizePercent(100);
    rf.setBatchSize("100");
    rf.setBreakTiesRandomly(false);
    rf.setCalcOutOfBag(false);
    rf.setDebug(false);
    rf.setDoNotCheckCapabilities(false);
    rf.setMaxDepth(0);
    rf.setNumDecimalPlaces(2);
    rf.setNumFeatures(0);
    rf.setNumIterations(100);
    rf.setOutputOutOfBagComplexityStatistics(false);
    rf.setStoreOutOfBagPredictions(false);
    return rf;
  }

  private static SMO getSmo() {
    SMO smo = new SMO();
    smo.setBatchSize("100");
    smo.setBuildCalibrationModels(false);
    smo.setC(1);
    smo.setChecksTurnedOff(false);
    smo.setDebug(false);
    smo.setDoNotCheckCapabilities(false);
    smo.setEpsilon(0.000000000001);
    smo.setNumDecimalPlaces(2);
    smo.setRandomSeed(1);
    smo.setNumFolds(-1);
    smo.setToleranceParameter(0.001);
    smo.setFilterType(new SelectedTag(SMO.FILTER_NORMALIZE, SMO.TAGS_FILTER));
    return smo;
  }

  public static AbstractClassifier getSMOPolyKernel() {
    SMO smo = getSmo();
    smo.setKernel(new PolyKernel());
    return smo;
  }

  public static AbstractClassifier getSMOPuk() {
    SMO smo = getSmo();
    smo.setKernel(new Puk());
    return smo;
  }

  public static AbstractClassifier getRegression() {
    LinearRegression classifier = new LinearRegression();
    classifier.setAttributeSelectionMethod(
        new SelectedTag(LinearRegression.SELECTION_NONE, LinearRegression.TAGS_SELECTION));
    classifier.setEliminateColinearAttributes(false);
    return classifier;
  }

  private static SMOreg getSMOReg() {
    SMOreg smo = new SMOreg();
    smo.setBatchSize("100");
    smo.setC(1);
    smo.setDebug(false);
    smo.setDoNotCheckCapabilities(false);
    smo.setNumDecimalPlaces(2);
    smo.setFilterType(new SelectedTag(SMO.FILTER_NORMALIZE, SMO.TAGS_FILTER));
    return smo;
  }

  public static AbstractClassifier getSMORegPolyKernel() {
    SMOreg smo = getSMOReg();
    smo.setKernel(new PolyKernel());
    return smo;
  }

  public static AbstractClassifier getSMORegPuk() {
    SMOreg smo = getSMOReg();
    smo.setKernel(new Puk());
    return smo;
  }

  //  set k = sqrt(size), size = number instances, wwight set 1/n
  public static AbstractClassifier getKnnCV() {
    IBk ibk = new IBk();
    ibk.setBatchSize("100");
    ibk.setDebug(false);
    ibk.setDoNotCheckCapabilities(false);

    ibk.setCrossValidate(true);
    ibk.setDistanceWeighting(new SelectedTag(IBk.WEIGHT_INVERSE, IBk.TAGS_WEIGHTING));
    ibk.setMeanSquared(false);
    ibk.setNumDecimalPlaces(2);
    ibk.setWindowSize(0);

    NearestNeighbourSearch nns = new LinearNNSearch();
    nns.setMeasurePerformance(false);
    EuclideanDistance ed = new EuclideanDistance();
    ed.setDontNormalize(false);
    ed.setInvertSelection(false);
    ed.setAttributeIndices("first-last");
    try {
      nns.setDistanceFunction(ed);
    } catch (Exception e) {
      logger.warn(e);
    }
    ibk.setNearestNeighbourSearchAlgorithm(nns);
    return ibk;
  }

  public static AbstractClassifier getBayesNet() {
    BayesNet by = new BayesNet();
    by.setBatchSize("100");
    by.setDebug(false);
    by.setDoNotCheckCapabilities(false);
    by.setNumDecimalPlaces(2);

    by.setUseADTree(false);

    BayesNetEstimator bne = new SimpleEstimator();
    bne.setAlpha(0.5);
    by.setEstimator(bne);

    K2 sa = new K2();
    sa.setInitAsNaiveBayes(true);
    sa.setMarkovBlanketClassifier(false);
    sa.setMaxNrOfParents(1);
    sa.setRandomOrder(false);
    sa.setScoreType(new SelectedTag(Scoreable.BAYES, LocalScoreSearchAlgorithm.TAGS_SCORE_TYPE));
    by.setSearchAlgorithm(sa);
    return by;
  }

  public static AbstractClassifier getAdditiveRegressionRF() {
    AdditiveRegression ab = new AdditiveRegression();
    ab.setClassifier(getRandomForest());
    ab.setNumIterations(10);
    return ab;
  }

  public static AbstractClassifier getAdditiveRegressionKnn() {
    AdditiveRegression ab = new AdditiveRegression();
    ab.setClassifier(getKnnCV());
    ab.setNumIterations(100);
    return ab;
  }

  public static AbstractClassifier getAdditiveRegressionSMOReg() {
    AdditiveRegression ab = new AdditiveRegression();
    ab.setClassifier(getSMORegPuk());
    ab.setNumIterations(100);
    return ab;
  }

  public static AbstractClassifier getLogitBoost() {
    LogitBoost lb = new LogitBoost();
    lb.setBatchSize("100");
    lb.setDebug(false);
    lb.setDoNotCheckCapabilities(false);
    lb.setNumDecimalPlaces(2);

    lb.setNumIterations(10);
    lb.setSeed(1);
    lb.setUseResampling(false);
    lb.setWeightThreshold(100);
    lb.setClassifier(getRandomForest()); // set random forest

    lb.setZMax(3);
    lb.setLikelihoodThreshold(-1.7976931348623157E308);
    lb.setNumThreads(1);
    lb.setPoolSize(1);
    lb.setShrinkage(1);
    lb.setUseEstimatedPriors(false);
    lb.setUseResampling(false);
    return lb;
  }

  public static AbstractClassifier getAdaBoostM1() {
    AdaBoostM1 ab = new AdaBoostM1();
    ab.setBatchSize("100");
    ab.setDebug(false);
    ab.setDoNotCheckCapabilities(false);
    ab.setNumDecimalPlaces(2);

    ab.setNumIterations(10);
    ab.setSeed(1);
    ab.setUseResampling(false);
    ab.setWeightThreshold(100);
    ab.setClassifier(getSMOPuk()); // set with SMoPUk
    return ab;
  }

  public static AbstractClassifier getRandomCommitteeRandomForest() {
    RandomCommittee rc = new RandomCommittee();
    rc.setClassifier(getRandomForest());
    rc.setNumIterations(10);
    return rc;
  }
}
