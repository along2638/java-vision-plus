package com.github.javpower.javavision.arcsoft.entity;

import com.arcsoft.face.Rect;
import lombok.Data;

import java.util.List;

@Data
public class FaceDetectObject {
    private Rect rect;
    private int orient;
    private int faceId = -1;
    private int age = -1;
    private int gender = -1;
    private int liveness = -1;

    private List<Float> faceFeature;
}
