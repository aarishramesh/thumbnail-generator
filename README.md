# Thumbnail generator

Spring-boot application which watches S3 bucket for images, generates thumbnails (resolution 640 x 480 while the aspect ratio is maintained) of images on upload to S3 and uploads to a different bucket

The application provides UI for uploading an image, viewing a generated thumbnail, viewing original image by inputing its file name

==========
Tech stack
==========

-> Spring boot - version 1.5.9 as Java microservice framework
-> AWS S3 for object store
-> Thumbnailator version 0.4 for thumbnail generation of images
-> Spring AWS integration and spring integration for S3 polling
-> Thymeleaf for static resource generation
-> Maven as build tool