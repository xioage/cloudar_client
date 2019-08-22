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
int numClusters = 8;
int dimension;
float *means, *covariances, *priors;
double *omega, *miu;

std::vector<cv::Mat> descVector;
cv::Mat descMat;
cv::Mat descMatPCA;
std::vector<float *> encs;
cv::PCA pca;

double pi[K];
double mu[K][D];

JNIEXPORT jstring JNICALL Java_org_vlfeat_VLFeat_version(JNIEnv *env, jobject instance) {
    return env->NewStringUTF(vl_get_version_string());
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_addImage(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat descriptor(*((cv::Mat *) descriptors));
    cv::Mat desc;
    if(descriptor.rows > 1000) desc = descriptor.rowRange(0, 1000);
    else desc = descriptor;
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

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_loadPCA(JNIEnv *env, jobject instance, jstring path, jint featureType) {
    FILE *proFile, *procFile, *eigenFile;
    float *pro, *proc, *eigen;
    std::string proPath, proCenterPath, eigenPath;
    std::string name[2][3] = {{"sift_pro_pca", "sift_proc_pca", "sift_eigen_pca"}, {"surf_pro_pca", "surf_proc_pca", "surf_eigen_pca"}};
    int cols;

    if (featureType == 0) {
        dimension = 80;
        cols = 128;
    }
    else {
        dimension = 45;
        cols = 64;
    }

    proPath = convertJString(env, path).append(name[featureType][0]);
    proFile = fopen(proPath.c_str(), "rb");
    pro = (float *) malloc(sizeof(float) * dimension * cols);
    fread(pro, sizeof(float) * dimension * cols, 1, proFile);
    fclose(proFile);
    cv::Mat proMat(dimension, cols, CV_32FC1);
    for(int i = 0; i < dimension; i++) {
        for(int j = 0; j < cols; j++) {
            proMat.at<float>(i,j) = pro[i*cols+j];
        }
    }

    proCenterPath = convertJString(env, path).append(name[featureType][1]);
    procFile = fopen(proCenterPath.c_str(), "rb");
    proc = (float *) malloc(sizeof(float) * cols);
    fread(proc, sizeof(float) * cols, 1, procFile);
    fclose(procFile);
    cv::Mat procMat(1, cols, CV_32FC1);
    for(int i = 0; i < cols; i++) {
        procMat.at<float>(0, i) = proc[i];
    }

    eigenPath = convertJString(env, path).append(name[featureType][1]);
    eigenFile = fopen(eigenPath.c_str(), "rb");
    eigen = (float *) malloc(sizeof(float) * dimension);
    fread(eigen, sizeof(float) * dimension, 1, eigenFile);
    fclose(eigenFile);
    cv::Mat eigenMat(dimension, 1, CV_32FC1);
    for(int i = 0; i < dimension; i++) {
        eigenMat.at<float>(i, 0) = eigen[i];
    }

    pca = cv::PCA();
    pca.eigenvalues = eigenMat;
    pca.eigenvectors = proMat;
    pca.mean = procMat;
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "PCA loading finished");
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

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_loadGMM(JNIEnv *env, jobject instance, jstring path, jint featureType, jboolean isPCAEnabled) {
    FILE *meansFile, *covariancesFile, *priorsFile;
    std::string meansPath, covariancesPath, priorsPath;
    std::string name[2][3] = {{"sift_mean", "sift_cov", "sift_pri"}, {"surf_mean", "surf_cov", "surf_pri"}};

    if(isPCAEnabled) {
        if (featureType == 0) dimension = 80;
        else dimension = 45;
    } else {
        if (featureType == 0) dimension = 128;
        else dimension = 64;
    }

    meansPath = convertJString(env, path).append(name[featureType][0]);
    meansFile = fopen(meansPath.c_str(), "rb");
    means = (float *) malloc(sizeof(float) * dimension * numClusters);
    fread(means, sizeof(float) * dimension * numClusters, 1, meansFile);
    fclose(meansFile);

    covariancesPath = convertJString(env, path).append(name[featureType][1]);
    covariancesFile = fopen(covariancesPath.c_str(), "rb");
    covariances = (float *) malloc(sizeof(float) * dimension * numClusters);
    fread(covariances, sizeof(float) * dimension * numClusters, 1, covariancesFile);
    fclose(covariancesFile);

    priorsPath = convertJString(env, path).append(name[featureType][2]);
    priorsFile = fopen(priorsPath.c_str(), "rb");
    priors = (float *) malloc(sizeof(float) * numClusters);
    fread(priors, sizeof(float) * numClusters, 1, priorsFile);
    fclose(priorsFile);

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "GMM loading finished");
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
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "image %d encoded", i);

        free(encData);
    }
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "database encoded");
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_FVEncodeDatabaseGMMNoPCA(JNIEnv *env, jobject instance) {
    dimension = descVector[0].cols;

    for (int i = 0; i < descVector.size(); i++) {
        float *enc = (float *) vl_malloc(sizeof(float) * 2 * dimension * numClusters);
        vl_fisher_encode(enc, VL_TYPE_FLOAT,
                         means, dimension, numClusters,
                         covariances, priors,
                         descVector[i].ptr<float>(0), descVector[i].rows,
                         VL_FISHER_FLAG_IMPROVED);

        encs.push_back(enc);
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "image %d encoded", i);
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

JNIEXPORT jintArray JNICALL Java_org_vlfeat_VLFeat_matchGMMNoPCA(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat desc(*((cv::Mat *) descriptors));
    dimension = desc.cols;

    float *enc = (float *) vl_malloc(sizeof(float) * 2 * dimension * numClusters);
    vl_fisher_encode(enc, VL_TYPE_FLOAT,
                     means, dimension, numClusters,
                     covariances, priors,
                     desc.ptr<float>(0), desc.rows,
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

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_loadBMM(JNIEnv *env, jobject instance, jstring path, jint featureType) {
    FILE *omegaFile, *miuFile;
    std::string omegaPath, miuPath;
    std::string name[3][2] = {{"orb_omega", "orb_miu"}, {"freak_omega", "freak_miu"}, {"brisk_omega", "brisk_miu"}};

    omegaPath = convertJString(env, path).append(name[featureType-2][0]);
    omegaFile = fopen(omegaPath.c_str(), "rb");
    omega = (double *) malloc(sizeof(double) * K);
    fread(omega, sizeof(double) * K, 1, omegaFile);
    fclose(omegaFile);

    for (int i = 0; i < K; i++)
        pi[i] = omega[i];

    miuPath = convertJString(env, path).append(name[featureType-2][1]);
    miuFile = fopen(miuPath.c_str(), "rb");
    miu = (double *) malloc(sizeof(double) * K * D);
    fread(miu, sizeof(double) * K * D, 1, miuFile);
    fclose(miuFile);

    for (int i = 0; i < K; i++)
        for (int j = 0; j < D; j++)
            mu[i][j] = miu[i * D + j];

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "BMM loading finished");
}

JNIEXPORT void JNICALL Java_org_vlfeat_VLFeat_FVEncodeDatabaseBMM(JNIEnv *env, jobject instance) {
    for(int i = 0; i < descVector.size(); i++) {
        float *enc = (float *)malloc((D+1)*K * sizeof(float));
        FV(descVector[i].data, descVector[i].rows, pi, mu, enc);
        encs.push_back(enc);
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "image %d encoded", i);
    }
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "database encoded");
}

JNIEXPORT jintArray JNICALL Java_org_vlfeat_VLFeat_matchBMM(JNIEnv *env, jobject instance, jlong descriptors) {
    cv::Mat desc(*((cv::Mat *) descriptors));
    float enc[(D+1)*K];

    FV(desc.data, desc.rows, pi, mu, enc);
    double distances[NN];
    int indexes[NN] = {0};

    for(int i = 0; i < encs.size(); i++) {
        double distance = norm(enc, encs[i], (D+1)*K);
        if(i == 0) {
            for (int j = 0; j < NN; j++)
                distances[j] = distance;
        }

        sort(distances, indexes, distance, i, NN);
        //__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "distance: %lf", distance);
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