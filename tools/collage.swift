// Fügt Screenshots nebeneinander zu einer Collage mit Überschrift zusammen (nur macOS-Bordmittel).
// Aufruf: swift tools/collage.swift <out.png> "<Titel>" <bild1.png> [<bild2.png> ...]
import AppKit
import CoreText

let args = CommandLine.arguments
guard args.count >= 4 else {
    FileHandle.standardError.write("usage: collage.swift <out.png> <title> <img>...\n".data(using: .utf8)!)
    exit(1)
}
let outPath = args[1]
let title = args[2]
let files = Array(args[3...])

let tileW: CGFloat = 540, gap: CGFloat = 28, titleH: CGFloat = 120, radius: CGFloat = 32

func load(_ path: String) -> CGImage {
    guard let src = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil),
          let img = CGImageSourceCreateImageAtIndex(src, 0, nil) else {
        FileHandle.standardError.write("cannot read \(path)\n".data(using: .utf8)!)
        exit(1)
    }
    return img
}

let images = files.map(load)
let heights = images.map { tileW * CGFloat($0.height) / CGFloat($0.width) }
let tileH = heights.max() ?? 0
let width = CGFloat(images.count) * tileW + CGFloat(images.count + 1) * gap
let height = titleH + tileH + gap

let ctx = CGContext(data: nil, width: Int(width), height: Int(height), bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
ctx.setFillColor(CGColor(red: 0.93, green: 0.94, blue: 0.96, alpha: 1))
ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))

// Überschrift (Ursprung unten links)
let font = CTFontCreateWithName("Helvetica-Bold" as CFString, 46, nil)
let attr = NSAttributedString(string: title, attributes: [
    .font: font, .foregroundColor: CGColor(red: 0.11, green: 0.12, blue: 0.15, alpha: 1)
])
let line = CTLineCreateWithAttributedString(attr)
ctx.textPosition = CGPoint(x: gap, y: height - titleH + 38)
CTLineDraw(line, ctx)

// Screens mit abgerundeten Ecken, oben bündig
for (i, img) in images.enumerated() {
    let h = heights[i]
    let rect = CGRect(x: gap + CGFloat(i) * (tileW + gap), y: gap + (tileH - h), width: tileW, height: h)
    ctx.saveGState()
    ctx.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
    ctx.clip()
    ctx.interpolationQuality = .high
    ctx.draw(img, in: rect)
    ctx.restoreGState()
}

let out = ctx.makeImage()!
let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: outPath) as CFURL, "public.png" as CFString, 1, nil)!
CGImageDestinationAddImage(dest, out, nil)
CGImageDestinationFinalize(dest)
