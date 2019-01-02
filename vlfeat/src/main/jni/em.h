#ifdef __cplusplus
extern "C" {
#endif

#ifndef EM_H
#define EM_H

#define D 256
#define K 64

void EM(char x[][D], double mu[K][D], double *pi, double z[][K], int N);

void ExpectationStep(double z[][K], double *pi, double mu[K][D], char x[][D], int N);
double ExpectationSubStep(int n, int k, double *pi, double mu[K][D], char x[][D]);

void MaximizationStep(double z[][K], double *pi, double mu[K][D], char x[][D], int N);
double *Average(int m, char x[][D], double z[][K], int N);
double Nm(int m, double z[][K], int N);

void FV(unsigned char* des, int T, double pi[K], double mu[K][D], float* vector);

#endif

#ifdef __cplusplus
}
#endif
