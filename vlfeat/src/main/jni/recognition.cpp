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
int numClusters = 32;
int dimension = 64;
float *means, *covariances, *priors;

std::vector<cv::Mat> descVector;
cv::Mat descMat;
cv::Mat descMatPCA;
std::vector<float *> encs;
cv::PCA pca;

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

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_trainPCA(JNIEnv *env, jobject instance) {
    pca = cv::PCA(descMat, cv::Mat(), 0, dimension);
    descMatPCA = pca.project(descMat);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "PCA training finished");
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_trainGMM(JNIEnv *env, jobject instance, jstring path) {
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
#if 0
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
#endif
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

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_FVEncodeDatabase(JNIEnv *env, jobject instance) {
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

double norm(float *a, float *b, int l) {
    double res = 0;
    for (int i = 0; i != l; ++i) {
        if(a[i] < 99999999 && b[i] < 99999999)
            res += (a[i] - b[i]) * (a[i] - b[i]);
    }
    return res;
}

JNIEXPORT jint JNICALL Java_org_vlfeat_VLFeat_match(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat desc(*((cv::Mat *) descriptors));
    cv::Mat descPCA = pca.project(desc);

    float *enc = (float *) vl_malloc(sizeof(float) * 2 * dimension * numClusters);
    vl_fisher_encode(enc, VL_TYPE_FLOAT,
                     means, dimension, numClusters,
                     covariances, priors,
                     descPCA.ptr<float>(0), descPCA.rows,
                     VL_FISHER_FLAG_IMPROVED);

    double minDistance;
    int n = 0;
    for (int i = 0; i < encs.size(); i++) {
        double distance = norm(enc, encs[i], 2 * dimension * numClusters);
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "distance: %f", distance);
        if(i == 0) {
            minDistance = distance;
        } else if (distance < minDistance) {
            n = i;
            minDistance = distance;
        }
    }

    free(enc);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "best index: %d", n);

    return n;
}
}