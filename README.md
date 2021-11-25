## CloudAR
CloudAR is a cloud-based framework for mobile augmented reality. This android client
can also work without the cloud recognition service, where the template matching is
done locally with a few images.

### Dependencies

* `OpenCV4Android` ver 3.x for basic vision processing
* `Rajawali` as the 3D renderring engine
* `ARCore` dev preview2 as the tracking service

### Structure

* `cloudar` , the app-independent CloudAR library codes
* `poster` , the poster App, defining app UI
* `sear` , the SEAR App, defining app UI
* `opencv` , opencv java wrapper
* `rajawali` , rajawali library
* `vlfeat` , vlfeat library

### Citing

If you find our work useful, please cite one of the followings:

```sh
@inproceedings{zhang2018jaguar,
  title={{Jaguar: Low Latency Mobile Augmented Reality with Flexible Tracking}},
  author={Wenxiao Zhang and Bo Han and Pan Hui},
  booktitle={Proceedings of ACM Multimedia},
  year={2018}
}

@inproceedings{zhang2018cars,
  title={{CARS: Collaborative Augmented Reality for Socialization}},
  author={Wenxiao Zhang and Bo Han and Pan Hui and Vijay  Gopalakrishnan and Eric Zavesky and Feng Qian},
  booktitle={Proceedings of International Workshop on Mobile Computing Systems and Applications (HotMobile)},
  year={2018}
}
```
