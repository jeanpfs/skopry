import AppKit

/// The status item's image: design/AppIcon.svg reduced to a menu bar template. Two windows stacked
/// at the left (the Mac's), and at the right the one that goes to the device, whose inside shows the
/// state: empty (idle), a dot (a device is connected), the arrow (streaming), or a badge at the
/// lower right (Skopry needs attention).
///
/// Drawn in code, so there is no asset to keep in sync, and marked as a template so the menu bar
/// tints it for light and dark bars and highlights it when the menu is open. `symbolFallback`
/// (SF Symbol macwindow.on.rectangle) is the stand-in if this drawing is ever dropped; the
/// previews render both side by side.
enum StatusGlyph {
    enum State: Equatable, CaseIterable { case idle, connected, streaming, attention }

    /// 18 pt tall: 16 pt of drawing and a point above and below, like the system's own items.
    static let size = NSSize(width: 21, height: 18)

    static let symbolFallback: NSImage? = {
        let image = NSImage(systemSymbolName: "macwindow.on.rectangle", accessibilityDescription: "Skopry")
        image?.isTemplate = true
        return image
    }()

    static func image(_ state: State) -> NSImage {
        let image = NSImage(size: size, flipped: true) { _ in
            guard let ctx = NSGraphicsContext.current?.cgContext else { return false }
            draw(state, in: ctx)
            return true
        }
        image.isTemplate = true
        image.accessibilityDescription = "Skopry"
        return image
    }

    // MARK: Geometry, in points (y down)

    private static let line: CGFloat = 1.3

    private static func outline(_ r: CGRect, _ radius: CGFloat) -> CGPath {
        CGPath(roundedRect: r.insetBy(dx: line / 2, dy: line / 2), cornerWidth: radius, cornerHeight: radius, transform: nil)
    }

    private static func draw(_ state: State, in ctx: CGContext) {
        ctx.setFillColor(NSColor.black.cgColor)
        ctx.setStrokeColor(NSColor.black.cgColor)
        ctx.setLineWidth(line)
        ctx.setLineCap(.round)
        ctx.setLineJoin(.round)

        // The Mac's two windows, stacked at the left.
        let top = CGRect(x: 0.5, y: 1.5, width: 9, height: 6.5)
        let bottom = CGRect(x: 0.5, y: 10, width: 9, height: 6.5)
        ctx.addPath(outline(top, 1.8)); ctx.strokePath()
        ctx.addPath(outline(bottom, 1.8)); ctx.strokePath()

        // The window that goes to the device, in front: it clears what is behind it, then shows the state.
        let front = CGRect(x: 7, y: 5, width: 13.5, height: 8.5)
        ctx.setBlendMode(.clear)
        ctx.addPath(CGPath(roundedRect: front.insetBy(dx: -1.4, dy: -1.4), cornerWidth: 3, cornerHeight: 3, transform: nil))
        ctx.fillPath()
        ctx.setBlendMode(.normal)
        ctx.addPath(outline(front, 2.2)); ctx.strokePath()

        let c = CGPoint(x: front.midX, y: front.midY + 0.6)
        switch state {
        case .connected:
            let d: CGFloat = 3.2
            ctx.fillEllipse(in: CGRect(x: c.x - d / 2, y: c.y - d / 2, width: d, height: d))
        case .streaming:
            ctx.move(to: CGPoint(x: c.x - 3.6, y: c.y)); ctx.addLine(to: CGPoint(x: c.x + 3.4, y: c.y))
            ctx.move(to: CGPoint(x: c.x + 0.8, y: c.y - 2.6)); ctx.addLine(to: CGPoint(x: c.x + 3.6, y: c.y)); ctx.addLine(to: CGPoint(x: c.x + 0.8, y: c.y + 2.6))
            ctx.strokePath()
        case .idle, .attention:
            break
        }

        // Attention: a dot at the lower right, cut out of whatever it overlaps.
        if state == .attention {
            let d = size.height * 0.42
            let dot = CGRect(x: size.width - d, y: size.height - d, width: d, height: d)
            ctx.setBlendMode(.clear)
            ctx.fillEllipse(in: dot.insetBy(dx: -1.5, dy: -1.5))
            ctx.setBlendMode(.normal)
            ctx.fillEllipse(in: dot)
        }
    }
}
