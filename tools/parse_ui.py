import sys
import xml.etree.ElementTree as ET


def main(path):
    root = ET.parse(path).getroot()
    for i, n in enumerate(root.iter("node")):
        text = n.get("text", "")
        desc = n.get("content-desc", "")
        cls = n.get("class", "")
        rid = n.get("resource-id", "")
        bounds = n.get("bounds", "")
        clickable = n.get("clickable", "")
        enabled = n.get("enabled", "")
        scrollable = n.get("scrollable", "")
        if text or desc or clickable == "true" or scrollable == "true":
            print(
                f"{i:03d} class={cls} text={text!r} desc={desc!r} "
                f"id={rid!r} bounds={bounds} clickable={clickable} "
                f"enabled={enabled} scrollable={scrollable}"
            )


if __name__ == "__main__":
    main(sys.argv[1])
