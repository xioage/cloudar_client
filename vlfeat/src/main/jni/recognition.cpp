//
// Created by wzhangal on 7/5/2017.
//

#include <jni.h>
#include <vector>
#include <string>
#include <opencv2/core/core.hpp>
#include <vl/generic.h>
#include <vl/gmm.h>
#include <vl/fisher.h>
#include <android/log.h>

#define LOG_TAG "VLEncoding"

extern "C" {
VlGMM *gmm;
int numClusters = 32;
int dimension;
int numData;
float *means, *covariances, *priors;

std::vector<cv::Mat> descVector;
cv::Mat descMat;
std::vector<cv::Mat> FVs;
std::vector<float *> encs;

JNIEXPORT jstring JNICALL Java_org_vlfeat_VLFeat_version(JNIEnv *env, jobject instance) {
    return env->NewStringUTF(vl_get_version_string());
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_addImage(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat desc(*((cv::Mat *) descriptors));
    descVector.push_back(desc);
    descMat.push_back(desc);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "desc added: %d", desc.rows);
}

std::string convertJString(JNIEnv* env, jstring str) {
    const jsize len = env->GetStringUTFLength(str);
    const char* strChars = env->GetStringUTFChars(str, (jboolean *)0);

    std::string result(strChars, len);
    env->ReleaseStringUTFChars(str, strChars);

    return result;
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_trainGMM(JNIEnv *env, jobject instance, jstring path) {
    FILE *meansFile, *covariancesFile, *priorsFile;
    std::string meansPath, covariancesPath, priorsPath;

    dimension = descMat.cols;
    numData = descMat.rows;

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "training desc: %d", numData);

    float *trainData = (float *) vl_malloc(sizeof(float) * dimension * numData);
    for (int i = 0; i < numData; i++) {
        //memcpy(trainData+i*dimension*sizeof(float), descMat.ptr<float>(i), dimension*sizeof(float));
        for (int j = 0; j < dimension; j++) {
            trainData[i * dimension + j] = descMat.at<float>(i, j);
        }
    }

    // create a GMM object and cluster input data to get means, covariances
    // and priors of the estimated mixture
    VlKMeans *kmeans = vl_kmeans_new(VL_TYPE_FLOAT, VlDistanceL2);
    vl_kmeans_set_algorithm(kmeans, VlKMeansElkan);
    vl_kmeans_set_initialization(kmeans, VlKMeansPlusPlus);
    vl_kmeans_set_max_num_iterations(kmeans, 100);
    gmm = vl_gmm_new(VL_TYPE_FLOAT, dimension, numClusters);
    vl_gmm_set_initialization(gmm, VlGMMKMeans);
    vl_gmm_set_kmeans_init_object(gmm, kmeans);
    vl_gmm_cluster(gmm, trainData, numData);

    means = (float *)vl_gmm_get_means(gmm);
    covariances = (float *)vl_gmm_get_covariances(gmm);
    priors = (float *)vl_gmm_get_priors(gmm);

    meansPath = convertJString(env, path).append("/CloudAR/means");
    meansFile = fopen(meansPath.c_str(), "wb");
    fwrite(means, sizeof(float) * dimension * numClusters, 1, meansFile);
    fclose(meansFile);

    covariancesPath = convertJString(env, path).append("/CloudAR/covariances");
    covariancesFile = fopen(covariancesPath.c_str(), "wb");
    fwrite(covariances, sizeof(float) * dimension * numClusters, 1, covariancesFile);
    fclose(covariancesFile);

    priorsPath = convertJString(env, path).append("/CloudAR/priors");
    priorsFile = fopen(priorsPath.c_str(), "wb");
    fwrite(priors, sizeof(float) * numClusters, 1, priorsFile);
    fclose(priorsFile);

    free(trainData);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "GMM training finished");
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_loadGMM(JNIEnv *env, jobject instance, jstring path) {
    FILE *meansFile, *covariancesFile, *priorsFile;
    std::string meansPath, covariancesPath, priorsPath;

    dimension = descMat.cols;

    meansPath = convertJString(env, path).append("/CloudAR/means");
    meansFile = fopen(meansPath.c_str(), "rb");
    means = (float *)malloc(sizeof(float) * dimension * numClusters);
    fread(means, sizeof(float) * dimension * numClusters, 1, meansFile);
    fclose(meansFile);

    covariancesPath = convertJString(env, path).append("/CloudAR/covariances");
    covariancesFile = fopen(covariancesPath.c_str(), "rb");
    covariances = (float *)malloc(sizeof(float) * dimension * numClusters);
    fread(covariances, sizeof(float) * dimension * numClusters, 1, covariancesFile);
    fclose(covariancesFile);

    priorsPath = convertJString(env, path).append("/CloudAR/priors");
    priorsFile = fopen(priorsPath.c_str(), "rb");
    priors = (float *)malloc(sizeof(float) * numClusters);
    fread(priors, sizeof(float) * numClusters, 1, priorsFile);
    fclose(priorsFile);

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "GMM loading finished");
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_FVEncodeDatabase(JNIEnv *env, jobject instance) {
    for (int i = 0; i < descVector.size(); i++) {
        // allocate space for the encoding
        float *enc = (float *) vl_malloc(sizeof(float) * 2 * dimension * numClusters);

        float *encData = (float *) vl_malloc(sizeof(float) * dimension * descVector[i].rows);
        for (int j = 0; j < descVector[i].rows; j++) {
            for (int k = 0; k < dimension; k++) {
                encData[j * dimension + k] = descVector[i].at<float>(j, k);
            }
        }

        // run fisher encoding
        vl_fisher_encode(enc, VL_TYPE_FLOAT,
                         means, dimension, numClusters,
                         covariances, priors,
                         encData, descVector[i].rows,
                         VL_FISHER_FLAG_IMPROVED);

        FVs.push_back(cv::Mat(1, 2 * dimension * numClusters, CV_32FC1, enc));
        encs.push_back(enc);

        free(encData);
    }
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "database encoded");
}

double norm(float *a, float *b, int l) {
    double res = 0;
    for (int i = 0; i != l; ++i) {
        if(a[i] < 999999 && b[i] < 999999)
        res += (a[i] - b[i]) * (a[i] - b[i]);
    }
    return res;
}

JNIEXPORT jint JNICALL Java_org_vlfeat_VLFeat_match(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat desc(*((cv::Mat *) descriptors));

    float *enc = (float *) vl_malloc(sizeof(float) * 2 * dimension * numClusters);

    // run fisher encoding
    vl_fisher_encode(enc, VL_TYPE_FLOAT,
                     means, dimension, numClusters,
                     covariances, priors,
                     desc.ptr<float>(0), desc.rows,
                     VL_FISHER_FLAG_IMPROVED);

    cv::Mat query = cv::Mat(1, 2 * dimension * numClusters, CV_32FC1, enc);

    double minDistance;
    int n = 0;
    for (int i = 0; i < FVs.size(); i++) {
        //double distance = cv::norm(query, FVs[i], cv::NORM_L2);
        double distance = norm(enc, encs[i], 2 * dimension * numClusters);
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "distance: %f", distance);
        if(i == 0)
            minDistance = distance;
        else if (distance < minDistance) {
            n = i;
            minDistance = distance;
        }
    }

    free(enc);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "best index: %d", n);

    return n;
}
}