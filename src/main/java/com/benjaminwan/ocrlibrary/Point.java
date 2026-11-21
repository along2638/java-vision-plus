package com.benjaminwan.ocrlibrary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * 图像坐标点（像素）。
 */
@Schema(description = "二维坐标点（像素）")
public class Point {
    @Schema(description = "横坐标，单位像素", example = "120")
    private int x;

    @Schema(description = "纵坐标，单位像素", example = "240")
    private int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point point = (Point) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Point{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
