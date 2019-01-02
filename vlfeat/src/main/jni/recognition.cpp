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
#include <vl/vlad.h>
#include <android/log.h>
#include "em.h"

#define LOG_TAG "vlfeat"
#define NN 1

extern "C" {
int numClusters = 32;
int dimension = 60;
float *means, *covariances, *priors;

std::vector<cv::Mat> descVector;
cv::Mat descMat;
cv::Mat descMatPCA;
std::vector<float *> encs;
cv::PCA pca;

double pi[K];
double mu[K][D];
double vector[10][16448];

JNIEXPORT jstring JNICALL Java_org_vlfeat_VLFeat_version(JNIEnv *env, jobject instance) {
    return env->NewStringUTF(vl_get_version_string());
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_addImage(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat descriptor(*((cv::Mat *) descriptors));
    cv::Mat desc = descriptor.rowRange(0, 300);
    descVector.push_back(desc);
    descMat.push_back(desc);
    //__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "desc added: %d", desc.rows);
}

std::string convertJString(JNIEnv* env, jstring str) {
    const jsize len = env->GetStringUTFLength(str);
    const char* strChars = env->GetStringUTFChars(str, (jboolean *)0);

    std::string result(strChars, len);
    env->ReleaseStringUTFChars(str, strChars);

    return result;
}

double norm(float *a, float *b, int l) {
    double res = 0;
    for (int i = 0; i != l; ++i) {
        if(a[i] > -999999 && a[i] < 999999 && b[i] > -999999 && b[i] < 999999)
            res += (a[i] - b[i]) * (a[i] - b[i]);
    }
    return res;
}

void sort(double *values, int *indexes, double value, int index, int k) {
    for(int i = k-1; i >= 0; i--) {
        if(value > values[i]) {
            if(i < k-1) {
                values[i+1] = value;
                indexes[i+1] = index;
            }
            break;
        } else {
            if(i == 0) {
                values[0] = value;
                indexes[0] = index;
            }
        }
    }
}

//===================================== GMM =========================================================

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_trainPCA(JNIEnv *env, jobject instance) {
    pca = cv::PCA(descMat, cv::Mat(), 0, dimension);
    descMatPCA = pca.project(descMat);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "PCA training finished");
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_trainGMM(JNIEnv *env, jobject instance) {
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "GMM training with %d descs", descMatPCA.rows);
    float *trainData = (float *) vl_malloc(sizeof(float) * dimension * descMat.rows);
    for (int i = 0; i < descMat.rows; i++) {
        //memcpy(trainData+i*dimension*sizeof(float), descMat.ptr<float>(i), dimension*sizeof(float));
        for (int j = 0; j < dimension; j++) {
            trainData[i * dimension + j] = descMatPCA.at<float>(i, j);
        }
    }

    // create a GMM object and cluster input data to get means, covariances
    // and priors of the estimated mixture
    VlKMeans *kmeans = vl_kmeans_new(VL_TYPE_FLOAT, VlDistanceL2);
    vl_kmeans_set_algorithm(kmeans, VlKMeansElkan);
    vl_kmeans_set_initialization(kmeans, VlKMeansPlusPlus);
    vl_kmeans_set_max_num_iterations(kmeans, 100);
    VlGMM *gmm = vl_gmm_new(VL_TYPE_FLOAT, dimension, numClusters);
    vl_gmm_set_initialization(gmm, VlGMMKMeans);
    vl_gmm_set_kmeans_init_object(gmm, kmeans);
    vl_gmm_cluster(gmm, trainData, descMat.rows);

    means = (float *)vl_gmm_get_means(gmm);
    covariances = (float *)vl_gmm_get_covariances(gmm);
    priors = (float *)vl_gmm_get_priors(gmm);

    free(trainData);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "GMM training finished");
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_FVEncodeDatabaseGMM(JNIEnv *env, jobject instance) {
    for (int i = 0; i < descVector.size(); i++) {
        float *enc = (float *) vl_malloc(sizeof(float) * 2 * dimension * numClusters);

        float *encData = (float *) vl_malloc(sizeof(float) * dimension * descVector[i].rows);
        cv::Mat curMatPca = pca.project(descVector[i]);
        for (int j = 0; j < descVector[i].rows; j++) {
            for (int k = 0; k < dimension; k++) {
                encData[j * dimension + k] = curMatPca.at<float>(j, k);
            }
        }

        vl_fisher_encode(enc, VL_TYPE_FLOAT,
                         means, dimension, numClusters,
                         covariances, priors,
                         encData, descVector[i].rows,
                         VL_FISHER_FLAG_IMPROVED);

        encs.push_back(enc);

        free(encData);
    }
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "database encoded");
}

JNIEXPORT jintArray JNICALL Java_org_vlfeat_VLFeat_matchGMM(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat desc(*((cv::Mat *) descriptors));
    cv::Mat descPCA = pca.project(desc);

    float *enc = (float *) vl_malloc(sizeof(float) * 2 * dimension * numClusters);
    vl_fisher_encode(enc, VL_TYPE_FLOAT,
                     means, dimension, numClusters,
                     covariances, priors,
                     descPCA.ptr<float>(0), descPCA.rows,
                     VL_FISHER_FLAG_IMPROVED);

    double distances[NN];
    int indexes[NN] = {0};
    for (int i = 0; i < encs.size(); i++) {
        double distance = norm(enc, encs[i], 2 * dimension * numClusters);
        //__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "distance: %f", distance);
        if(i == 0) {
            for (int j = 0; j < NN; j++)
                distances[j] = distance;
        }

        sort(distances, indexes, distance, i, NN);
    }

    free(enc);

    jintArray result = env->NewIntArray(NN);
    env->SetIntArrayRegion(result, 0, NN, indexes);
    return result;
}

//===================================== BMM =========================================================

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_trainBMM(JNIEnv *env, jobject instance) {
    int N = descMat.rows;

    char (*descs)[D] = new char[N][D];
    uchar one = 1;

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "size of train: %d", N);

    for(int i = 0; i < N; i++) {
        for(int j = 0; j < 32; j++) {
            uchar byte = descMat.at<uchar>(i, j);
            for(int k = 0; k < 8; k++)
                descs[i][j*8+k] = (byte >> (7 - k)) & one;
        }
    }

    double (*z)[K] = new double[N][K];

    for (int i = 0; i < K; i++) {
        pi[i] = 1.0 / K;
        for(int j = 0; j < D; j++)
            mu[i][j] = 0;
    }
    for (int i = 0; i < N; i++) {
        for(int j = 0; j < K; j++)
            z[i][j] = 0;
    }

    EM(descs, mu, pi, z, N);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "training finished");
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_FVEncodeDatabaseBMM(JNIEnv *env, jobject instance) {
    for(int i = 0; i < descVector.size(); i++) {
        float enc[16448];
        FV(descVector[i].data, descVector[i].rows, pi, mu, enc);
        encs.push_back(enc);
    }
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "database encoded");
}

JNIEXPORT jintArray JNICALL Java_org_vlfeat_VLFeat_matchBMM(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat desc(*((cv::Mat *) descriptors));
    float enc[16448];

    FV(desc.data, desc.rows, pi, mu, enc);
    double distances[NN];
    int indexes[NN] = {0};

    for(int i = 0; i < encs.size(); i++) {
        double distance = norm(enc, encs[i], 16448);
        if(i == 0) {
            for (int j = 0; j < NN; j++)
                distances[j] = distance;
        }

        sort(distances, indexes, distance, i, NN);
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "distance: %lf", distance);
    }

    jintArray result = env->NewIntArray(NN);
    env->SetIntArrayRegion(result, 0, NN, indexes);
    return result;
}

#if 0
JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_trainGMM(JNIEnv *env, jobject instance, jstring path) {
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "GMM training with %d descs", descMatPCA.rows);
    float *trainData = (float *) vl_malloc(sizeof(float) * dimension * descMat.rows);
    for (int i = 0; i < descMat.rows; i++) {
        //memcpy(trainData+i*dimension*sizeof(float), descMat.ptr<float>(i), dimension*sizeof(float));
        for (int j = 0; j < dimension; j++) {
            trainData[i * dimension + j] = descMatPCA.at<float>(i, j);
        }
    }

    // create a GMM object and cluster input data to get means, covariances
    // and priors of the estimated mixture
    VlKMeans *kmeans = vl_kmeans_new(VL_TYPE_FLOAT, VlDistanceL2);
    vl_kmeans_set_algorithm(kmeans, VlKMeansElkan);
    vl_kmeans_set_initialization(kmeans, VlKMeansPlusPlus);
    vl_kmeans_set_max_num_iterations(kmeans, 100);
    VlGMM *gmm = vl_gmm_new(VL_TYPE_FLOAT, dimension, numClusters);
    vl_gmm_set_initialization(gmm, VlGMMKMeans);
    vl_gmm_set_kmeans_init_object(gmm, kmeans);
    vl_gmm_cluster(gmm, trainData, descMat.rows);

    means = (float *)vl_gmm_get_means(gmm);
    covariances = (float *)vl_gmm_get_covariances(gmm);
    priors = (float *)vl_gmm_get_priors(gmm);

    FILE *meansFile, *covariancesFile, *priorsFile;
    std::string meansPath, covariancesPath, priorsPath;

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
#endif
}