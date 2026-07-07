// Author: Xia Zihang
using System.Drawing.Drawing2D;

namespace RecordYourWellnessApp;

/// <summary>
/// Design tokens matching Android app colors exactly.
/// </summary>
public static class Theme
{
    // ── Primary palette (Macaron Teal) ─────────────────────────────
    public static Color Primary       => Color.FromArgb(0x45, 0xB5, 0xD6);
    public static Color PrimaryDark   => Color.FromArgb(0x2F, 0xA8, 0xC4);
    public static Color PrimaryLight  => Color.FromArgb(0x80, 0xD0, 0xE8);
    public static Color NavBar        => Color.FromArgb(0x1B, 0x7B, 0x9E);

    // ── Secondary palette (Macaron Mint) ───────────────────────────
    public static Color Secondary     => Color.FromArgb(0x64, 0xE0, 0xB5);
    public static Color SecondaryDark => Color.FromArgb(0x20, 0xC0, 0x90);

    // ── Background & Surface ───────────────────────────────────────
    public static Color Background    => Color.FromArgb(0xF8, 0xFA, 0xFC);
    public static Color Surface       => Color.White;
    public static Color SurfaceVariant=> Color.FromArgb(0xF1, 0xF5, 0xF9);

    // ── Text ───────────────────────────────────────────────────────
    public static Color TextPrimary   => Color.FromArgb(0x1E, 0x29, 0x3B);
    public static Color TextSecondary => Color.FromArgb(0x64, 0x74, 0x8B);
    public static Color TextHint      => Color.FromArgb(0x94, 0xA3, 0xB8);
    public static Color TextOnDark    => Color.FromArgb(0xF8, 0xFA, 0xFC);

    // ── Semantic ───────────────────────────────────────────────────
    public static Color Error         => Color.FromArgb(0xEF, 0x44, 0x44);
    public static Color Success       => Color.FromArgb(0x22, 0xC5, 0x5E);
    public static Color Warning       => Color.FromArgb(0xF5, 0x9E, 0x0B);
    public static Color Outline       => Color.FromArgb(0xCB, 0xD5, 0xE1);
    public static Color Divider       => Color.FromArgb(0xE2, 0xE8, 0xF0);

    // ── Card backgrounds ───────────────────────────────────────────
    public static Color SleepCardStart  => Color.FromArgb(0xF5, 0xF7, 0xFF);
    public static Color SleepCardMid    => Color.FromArgb(0xEB, 0xF1, 0xFE);
    public static Color SleepCardEnd    => Color.FromArgb(0xDC, 0xE6, 0xFD);
    public static Color SleepAccent     => Color.FromArgb(0x25, 0x63, 0xEB);

    public static Color ExerciseCardStart => Color.FromArgb(0xF5, 0xFC, 0xF7);
    public static Color ExerciseCardMid   => Color.FromArgb(0xE7, 0xF7, 0xEE);
    public static Color ExerciseCardEnd   => Color.FromArgb(0xD4, 0xF0, 0xE0);
    public static Color ExerciseAccent    => Color.FromArgb(0x16, 0xA3, 0x4A);

    // ── Chart colors ───────────────────────────────────────────────
    public static Color ChartSleepGradientTop    => Color.FromArgb(0x1B, 0x7B, 0x9E);
    public static Color ChartSleepGradientBottom => Color.FromArgb(0x93, 0xC5, 0xFD);
    public static Color ChartExerciseGradientTop    => Color.FromArgb(0x34, 0xC7, 0x59);
    public static Color ChartExerciseGradientBottom => Color.FromArgb(0x86, 0xEF, 0xAC);
    public static Color ChartTargetLine   => Color.FromArgb(0xF5, 0x9E, 0x0B);
    public static Color ChartGridLine     => Color.FromArgb(0xE2, 0xE8, 0xF0);
    public static Color ChartSparkline    => Color.FromArgb(0x25, 0x63, 0xEB);

    // ── Input field ────────────────────────────────────────────────
    public static Color InputGradientTop    => Color.FromArgb(0xED, 0xF0, 0xF4);
    public static Color InputGradientBottom => Color.FromArgb(0xF5, 0xF7, 0xFA);
    public static Color InputErrorBg        => Color.FromArgb(0xFE, 0xF2, 0xF2);

    // ── Nav bar ────────────────────────────────────────────────────
    public static Color NavText         => Color.FromArgb(0xCB, 0xD5, 0xE1);
    public static Color NavTextInactive => Color.FromArgb(0xB3, 0xE5, 0xFC);
    public static Color NavActiveBg     => Color.FromArgb(0x45, 0xB5, 0xD6);

    // ── Fonts ───────────────────────────────────────────────────────
    // Build Segoe UI fonts in PIXEL units. Point-size fonts get scaled twice under
    // AutoScaleMode.Dpi (once by the auto-scaler, once again by point→device-DPI
    // rendering), which makes large text overflow its fixed-size boxes and clip
    // ("Dashbo...", "Exe...", overlapping labels). Pixel fonts scale exactly once,
    // in lock-step with the control bounds, so nothing is clipped. The incoming size
    // is still expressed in the familiar "points" and converted to px (1pt = 96/72 px).
    public static Font Font(float pointSize, FontStyle style = FontStyle.Regular)
        => new Font("Segoe UI", pointSize * 96f / 72f, style, GraphicsUnit.Pixel);

    // ── Card gloss overlay draw ────────────────────────────────────
    public static void PaintCardGloss(Graphics g, Rectangle rect)
    {
        using var gloss = new System.Drawing.Drawing2D.LinearGradientBrush(
            new Point(rect.Left, rect.Top),
            new Point(rect.Right, rect.Bottom),
            Color.FromArgb(64, 255, 255, 255),
            Color.FromArgb(0, 255, 255, 255));
        var blend = new System.Drawing.Drawing2D.ColorBlend(3);
        blend.Colors = new[] { Color.FromArgb(64, 255, 255, 255), Color.FromArgb(0, 255, 255, 255), Color.FromArgb(8, 255, 255, 255) };
        blend.Positions = new[] { 0f, 0.5f, 1f };
        gloss.InterpolationColors = blend;
        g.FillRectangle(gloss, rect);
    }

    // ── Rounded rectangle helper ───────────────────────────────────
    public static GraphicsPath RoundedRect(Rectangle rect, int radius)
    {
        var path = new GraphicsPath();
        int d = radius * 2;
        path.AddArc(rect.X, rect.Y, d, d, 180, 90);
        path.AddArc(rect.Right - d, rect.Y, d, d, 270, 90);
        path.AddArc(rect.Right - d, rect.Bottom - d, d, d, 0, 90);
        path.AddArc(rect.X, rect.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        return path;
    }
}
