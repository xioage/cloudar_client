#include <stdlib.h>
#include <time.h>
#include <math.h>
#include <stdio.h>
#include "em.h"

void EM(char x[][D], double mu[K][D], double *pi, double z[][K], int N) {
    
    /* double pi[K] = {1 / K}; */
    /* double mu[K][D] = {{0}}; */

    srand(time(NULL));
    /* normalization */
    double normalizationFactor;
    for (int w = 0; w < K; w++) {
       normalizationFactor = 0;
       for (int g = 0; g < D; g++) {
           mu[w][g] = rand() / (double)RAND_MAX;
           /* printf("mu[%d][%d] = %f\n", w, g, mu[w][g]); */
           normalizationFactor = normalizationFactor + mu[w][g];
       }

       /* printf("normailizationFactor = %f\n", normalizationFactor); */
       for (int g = 0; g < D; g++) {
           mu[w][g] = mu[w][g] / normalizationFactor;
           /* printf("mu[%d][%d] = %f\n", w, g, mu[w][g]); */
       }
    }

    for (int i = 0; i < 5; i++) {
        ExpectationStep(z, pi, mu, x, N);
        MaximizationStep(z, pi, mu, x, N);
    }
}

void ExpectationStep(double z[][K], double *pi, double mu[K][D], char x[][D], int N) {
    double normalizationFactor;
    for (int n = 0; n < N; n++) {
        normalizationFactor = 0.0;

        for (int k = 0; k < K; k++) {
            z[n][k] = ExpectationSubStep(n, k, pi, mu, x);
            normalizationFactor = normalizationFactor + z[n][k];
        }

        for (int k = 0; k < K; k++) {
            if (normalizationFactor > 0.0) {
                z[n][k] = z[n][k] / normalizationFactor;
            } else {
                z[n][k] = 1.0 / (float)K;
            }
        }
    }
}

double ExpectationSubStep(int n, int k, double *pi, double mu[K][D], char x[][D]) {
    double z_nk = pi[k];
    for (int i = 0; i < D; i++) {
        z_nk = z_nk * pow(mu[k][i], x[n][i]) * pow(1.0 - mu[k][i], 1.0 - x[n][i]);      
    }
    /* printf("z_nk = %f\n", z_nk); */
    return z_nk;
}

void MaximizationStep(double z[][K], double *pi, double mu[K][D], char x[][D], int N) {
    for (int k = 0; k < K; k++) {
        pi[k] = Nm(k, z, N) / (double)N;
    } 
    double *average;
    for (int k = 0; k < K; k++) {
        average = Average(k, x, z, N);
        
        for (int i = 0; i < D; i++) {
            mu[k][i] = average[i];
        }
    }
    free(average);
}

double *Average(int m, char x[][D], double z[][K], int N) {
    double *result = (double*)malloc(sizeof(double) * D);
    memset(result, 0, sizeof(double) * D);
    for (int i = 0; i < D; i++) {
        for (int n = 0; n < N; n++) {
            result[i] = result[i] + z[n][m] * x[n][i];
        }
    }
    double currentNm = Nm(m, z, N);
    for (int i = 0; i < D; i++) {
        result[i] = result[i] / currentNm;
    }
    return result;
}

double Nm(int m, double z[][K], int N) {
    double result = 0.0;
    for (int n = 0; n < N; n++) {
        result = result + z[n][m];
    }
    return result;
}

void FV(unsigned char* des, int T, double pi[K], double mu[K][D], float* vector) {

    double gamma[T*K];
    double tmp_mu[D] = {0};

    unsigned char bit;
    unsigned char byte;
    unsigned char one = 1;
    double normal;

#if 0
    // calculate p_k(x_t) for each des
    for (int t = 0; t != T; ++t) {
        normal = 0;
        for (int k = 0; k != K; ++k) {
            double p_xt_k = 1;
            for (int d = 0; d != 32; ++d) {
                byte = des[(t << 5) + d];
                for (int subIdx = 0; subIdx < 8; ++subIdx) {
                    bit = (byte >> (7 - subIdx)) & one;
                    if(bit)
                        p_xt_k *= mu[k][(d << 3) + subIdx];
                    else
                        p_xt_k *= (1 - mu[k][(d << 3) + subIdx]);
                } //end for 8
            } //end for d
            //t<<6 is t*K, change according to K
            gamma[(t << 6) + k] = pi[k] * p_xt_k;
            normal += gamma[(t << 6) + k];
        }//end for k
        //calculate gamma(t,k)
        for (int k = 0; k < K; ++k) {
            gamma[(t << 6) + k] /= normal;
        }
    } // end for T

    // calculate G_{alpha_k} & G_{mu_kd}
    for (int k = 0; k != K; ++k) {
        double tmp_alpha = 0;
        for (int t = 0; t != T; ++t) {
            tmp_alpha += gamma[(t << 6) + k] - pi[k];
            for (int d = 0; d != 32; ++d) {
                byte = des[(t << 5) + d];
                for (int subIdx = 0; subIdx < 8; ++subIdx) {
                    bit = (byte >> (7 - subIdx)) & one;
                    double tmp = mu[k][(d << 3) + subIdx];
                    if(bit)
                        tmp_mu[(d << 3) + subIdx] += gamma[(t << 6) + k] * sqrt(1 / tmp - 1);
                    else
                        tmp_mu[(d << 3) + subIdx] -= gamma[(t << 6) + k] * sqrt(tmp / (1 - tmp));
                } //end for 8
            } //end for d
        }//end for T
        double deno = (T * sqrt(pi[k]));
        //G_alpha
        vector[k * (D + 1)] = tmp_alpha / deno;
        //G_mu_d
        for (int d = 1; d != D + 1; ++d) {
            vector[k * (D + 1) + d] = tmp_mu[d - 1] / deno;
        }
    }//end for K
#endif

#if 1
    unsigned char bit0, bit1, bit2, bit3, bit4, bit5, bit6, bit7;
    double r0, r1, r2, r3, r4, r5, r6, r7;
    double ga, deno;

    // calculate p_k(x_t) for each des
    for (int t = 0; t != T; ++t) {
        normal = 0;
        for (int k = 0; k != K; ++k) {
            for (int d = 0; d != 32; ++d) {
                byte = des[(t << 5) + d];

                bit0 = (byte >> 7) & one;
                bit1 = (byte >> 6) & one;
                bit2 = (byte >> 5) & one;
                bit3 = (byte >> 4) & one;
                bit4 = (byte >> 3) & one;
                bit5 = (byte >> 2) & one;
                bit6 = (byte >> 1) & one;
                bit7 = byte & one;

                if(bit0)
                    r0 = mu[k][(d << 3) + 0];
                else
                    r0 = (1 - mu[k][(d << 3) + 0]);

                if(bit1)
                    r1 = mu[k][(d << 3) + 1];
                else
                    r1 = (1 - mu[k][(d << 3) + 1]);

                if(bit2)
                    r2 = mu[k][(d << 3) + 2];
                else
                    r2 = (1 - mu[k][(d << 3) + 2]);

                if(bit3)
                    r3 = mu[k][(d << 3) + 3];
                else
                    r3 = (1 - mu[k][(d << 3) + 3]);

                if(bit4)
                    r4 = mu[k][(d << 3) + 4];
                else
                    r4 = (1 - mu[k][(d << 3) + 4]);

                if(bit5)
                    r5 = mu[k][(d << 3) + 5];
                else
                    r5 = (1 - mu[k][(d << 3) + 5]);

                if(bit6)
                    r6 = mu[k][(d << 3) + 6];
                else
                    r6 = (1 - mu[k][(d << 3) + 6]);

                if(bit7)
                    r7 = mu[k][(d << 3) + 7];
                else
                    r7 = (1 - mu[k][(d << 3) + 7]);
            } //end for d
            //t<<6 is t*K, change according to K
            gamma[(t << 6) + k] = pi[k] * r0 * r1 * r2 * r3 * r4 * r5 * r6 * r7;
            normal += gamma[(t << 6) + k];
        }//end for k
        //calculate gamma(t,k)
        for (int k = 0; k != K; ++k) {
            gamma[(t << 6) + k] /= normal;
        }
    } // end for T

    // calculate G_{alpha_k} & G_{mu_kd}
    for (int k = 0; k != K; ++k) {
        double tmp_alpha = 0;
        for (int t = 0; t != T; ++t) {
            tmp_alpha += gamma[(t << 6) + k] - pi[k];
            for (int d = 0; d != 32; ++d) {
                byte = des[(t << 5) + d];
                ga = gamma[(t << 6) + k];

                bit0 = (byte >> 7) & one;
                bit1 = (byte >> 6) & one;
                bit2 = (byte >> 5) & one;
                bit3 = (byte >> 4) & one;
                bit4 = (byte >> 3) & one;
                bit5 = (byte >> 2) & one;
                bit6 = (byte >> 1) & one;
                bit7 = byte & one;

                r0 = mu[k][(d << 3)];
                r1 = mu[k][(d << 3) + 1];
                r2 = mu[k][(d << 3) + 2];
                r3 = mu[k][(d << 3) + 3];
                r4 = mu[k][(d << 3) + 4];
                r5 = mu[k][(d << 3) + 5];
                r6 = mu[k][(d << 3) + 6];
                r7 = mu[k][(d << 3) + 7];

                if(bit0)
                    tmp_mu[(d << 3)] += ga * sqrt(1 / r0 - 1);
                else
                    tmp_mu[(d << 3)] -= ga * sqrt(r0 / (1 - r0));

                if(bit1)
                    tmp_mu[(d << 3) + 1] += ga * sqrt(1 / r1 - 1);
                else
                    tmp_mu[(d << 3) + 1] -= ga * sqrt(r1 / (1 - r1));

                if(bit2)
                    tmp_mu[(d << 3) + 2] += ga * sqrt(1 / r2 - 1);
                else
                    tmp_mu[(d << 3) + 2] -= ga * sqrt(r2 / (1 - r2));

                if(bit3)
                    tmp_mu[(d << 3) + 3] += ga * sqrt(1 / r3 - 1);
                else
                    tmp_mu[(d << 3) + 3] -= ga * sqrt(r3 / (1 - r3));

                if(bit4)
                    tmp_mu[(d << 3) + 4] += ga * sqrt(1 / r4 - 1);
                else
                    tmp_mu[(d << 3) + 4] -= ga * sqrt(r4 / (1 - r4));

                if(bit5)
                    tmp_mu[(d << 3) + 5] += ga * sqrt(1 / r5 - 1);
                else
                    tmp_mu[(d << 3) + 5] -= ga * sqrt(r5 / (1 - r5));

                if(bit6)
                    tmp_mu[(d << 3) + 6] += ga * sqrt(1 / r6 - 1);
                else
                    tmp_mu[(d << 3) + 6] -= ga * sqrt(r6 / (1 - r6));

                if(bit7)
                    tmp_mu[(d << 3) + 7] += ga * sqrt(1 / r7 - 1);
                else
                    tmp_mu[(d << 3) + 7] -= ga * sqrt(r7 / (1 - r7));
            } //end for d
        }//end for T
        deno = (T * sqrt(pi[k]));
        //G_alpha
        vector[k * (D + 1)] = tmp_alpha / deno;
        //G_mu_d
        for (int d = 1; d != D + 1; ++d) {
            vector[k * (D + 1) + d] = tmp_mu[d - 1] / deno;
        }
    }//end for K
#endif
}