## CloudAR 
CloudAR is a cloud-based framework for mobile augmented reality. This android client can also work without the cloud recognition service, where the template matching is done locally with a few images.

### Dependencies

OpenCV4Android ver 3.x for basic vision processing
Rajawali as the 3D renderring engine

### Structure

app , the source code folder
  CloudAR , the app-independent CloudAR codes
  marker , marker definition
  network , send\receive packets
  renderer , rajawali wrapper
  template , useful 3D templates
  track , vision-based tracking and template matching
    MatchingTask , local template matching codes
  posterApp , the app-dependent codes, defining app UI
opencv , opencv java wrapper
rajawali , rajawali library
