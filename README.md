# JavaVision
[![GitHub stars](https://img.shields.io/github/stars/javpower/JavaVision.svg)](https://github.com/javpower/JavaVision)
[![GitHub issues](https://img.shields.io/github/issues/javpower/JavaVision.svg)](https://github.com/javpower/JavaVision/issues)
[![Apache License 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/java-1.8+-orange.svg)](https://adoptopenjdk.net/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-blue.svg)](https://spring.io/projects/spring-boot)
## 🪧 项目起源

- JavaVision是一个基于Java开发的全能视觉智能识别项目。该项目起源于对图像处理和人工智能领域的热情，以及对Java作为主要编程语言的坚持。在AI领域，大多数解决方案都是使用Python实现的，因此决定充分利用Java的优势来构建一个功能强大且易于集成的视觉智能识别平台。

  >JavaVision是完全开源、免费并支持多平台部署视觉智能识别项目
  >如果该项目对您有帮助，您的star是我不断优化的动力！！！
  >免费使用 OCR，https://ocr.m78cloud.cn



## 👏 项目特点

JavaVision具有以下突出特点：

- 框架: SpringBoot

- 多功能性：JavaVision不仅实现PaddleOCR-V4、YoloV8物体识别、人脸识别、以图搜图等核心功能，还可以轻松扩展到其他领域，如语音识别、动物识别、安防检查等。这使得JavaVision成为一个全面解决多种场景需求的自适应平台,支持 Es和 Milvus数据库的特征向量搜素。

- 高性能：JavaVision经过精心优化，具备卓越的性能表现。通过巧妙地结合算法优化和多线程技术，JavaVision能够在短时间内处理大量复杂的图像数据，并快速准确地进行识别和分析。

- 可靠稳定：Java作为一种成熟而可靠的编程语言，具备出色的稳定性和安全性。JavaVision严格遵循最佳实践和高标准，确保项目的稳定运行，并提供可靠的数据保护和隐私保密。

- 易于集成：JavaVision提供简洁而强大的API接口，使得与其他系统和平台的集成变得轻松快捷。无论是嵌入到现有应用程序中，还是作为独立的服务进行部署，JavaVision都能够与各种技术栈和框架无缝衔接。

- 灵活可拓展：JavaVision采用模块化设计，允许用户根据自己的需求进行灵活的定制和扩展。用户可以根据项目需求选择合适的功能模块，也可以通过插件机制添加新的识别算法和特性。

  > JavaVision旨在为Java开发者提供一个全面的视觉智能识别解决方案，让他们能够以熟悉且喜爱的编程语言构建出先进、可靠且易于集成的AI应用。无论是企业级应用还是个人项目，JavaVision都能助力开发者实现更多可能性

## 🔝 进阶使用

docker一键部署

- docker pull javpower/pdocr:v4.0.1
- docker run --name mycontainer -p 6888:6888 javpower/pdocr:v4.0.1


## 🔝 项目已实现

### 一、OCR应用

#### 1、YOLOV8识别物体
#### 2、YOLOV8识别物体
#### 3、DJL识别文字
#### 4、DJL识别文字
#### 5、反光衣检测
#### 6、JNI识别文字
#### 7、JNI识别文字
#### 8、火焰检测
#### 9、安全帽检测

### 二、人脸应用

#### 1、更新人脸
#### 2、人脸查询
#### 3、删除人脸
#### 4、添加人脸

### 三、以图搜图应用（ES版本&&Milvus版本）

#### 1、搜图
#### 2、添加图片
