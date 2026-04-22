import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ApplicationWindow {
    id: root
    visible: true
    width: 1024
    height: 600
    title: "Skoda Music MVP"
    color: "#0B121A"

    font.family: "Droid Sans"
    font.pixelSize: 16

    property string currentPage: "home"
    property bool isPlaying: false
    property real playbackPosition: 0.31
    property int trackDurationSec: 296
    property var lyricLines: []
    property int currentLyricIndex: -1
    property var currentTrack: ({
        title: "Morning Drive",
        artist: "Skoda Band"
    })
    property string lyrics: "[00:01.00]Morning Drive - Skoda Band\n[00:05.00]City lights slide across the glass\n[00:12.00]Hands on wheel, rhythm starts to rise\n[00:20.00]Road and music stay in sync tonight"
    property var recommendations: [
        { title: "Road Mix 1", artist: "Server" },
        { title: "Road Mix 2", artist: "Server" },
        { title: "Night Cruise", artist: "Skoda Band" },
        { title: "Sunset Loop", artist: "Skoda Band" },
        { title: "Highway Echo", artist: "Server" },
        { title: "After Rain", artist: "Server" }
    ]

    readonly property color glassPanelTop: "#2AFFFFFF"
    readonly property color glassPanelBottom: "#12FFFFFF"
    readonly property color glassBorder: "#3E95A6B8"
    readonly property color accent: "#5FD3FF"
    readonly property color accentSoft: "#2A9FD8"
    readonly property color textPrimary: "#EDF4FA"
    readonly property color textSecondary: "#A8BAC8"
    readonly property color buttonDark: "#2A3642"

    function openPage(pageName) {
        currentPage = pageName
        if (typeof bridge !== "undefined" && bridge.navigateToPageName) {
            bridge.navigateToPageName(pageName)
        }
    }

    function parseLyrics(raw) {
        var rows = []
        var text = raw || ""
        var lines = text.split("\n")
        for (var i = 0; i < lines.length; i += 1) {
            var line = lines[i]
            var matched = /^\[(\d+):(\d+(?:\.\d+)?)\](.*)$/.exec(line)
            if (matched) {
                var sec = parseInt(matched[1], 10) * 60 + parseFloat(matched[2])
                var content = matched[3].trim()
                rows.push({
                    time: sec,
                    text: content.length > 0 ? content : "..."
                })
            } else if (line.trim().length > 0) {
                var fallback = rows.length > 0 ? rows[rows.length - 1].time + 5 : 0
                rows.push({
                    time: fallback,
                    text: line.trim()
                })
            }
        }
        if (rows.length === 0) {
            rows.push({ time: 0, text: "暂无歌词" })
        }
        return rows
    }

    function updateCurrentLyricIndex() {
        if (!lyricLines || lyricLines.length === 0) {
            currentLyricIndex = -1
            return
        }
        var now = playbackPosition * trackDurationSec
        var idx = -1
        for (var i = 0; i < lyricLines.length; i += 1) {
            if (now >= lyricLines[i].time) {
                idx = i
            } else {
                break
            }
        }
        currentLyricIndex = idx < 0 ? 0 : idx
    }

    function rebuildLyrics() {
        lyricLines = parseLyrics(lyrics)
        updateCurrentLyricIndex()
    }

    function seekNormalized(v) {
        playbackPosition = Math.max(0, Math.min(1, v))
        updateCurrentLyricIndex()
    }

    onLyricsChanged: rebuildLyrics()
    onPlaybackPositionChanged: updateCurrentLyricIndex()
    Component.onCompleted: rebuildLyrics()

    Timer {
        interval: 1000
        repeat: true
        running: root.isPlaying
        onTriggered: {
            var step = root.trackDurationSec > 0 ? 1 / root.trackDurationSec : 0
            var next = root.playbackPosition + step
            root.playbackPosition = next >= 1 ? 0 : next
        }
    }

    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0.0; color: "#172536" }
            GradientStop { position: 0.65; color: "#0D1825" }
            GradientStop { position: 1.0; color: "#0A121C" }
        }
    }

    Rectangle {
        width: 640
        height: 640
        x: -200
        y: -280
        radius: 320
        color: "#1A5FD3FF"
    }

    Rectangle {
        width: 520
        height: 520
        x: root.width - 280
        y: root.height - 300
        radius: 260
        color: "#1441D6A4"
    }

    RowLayout {
        anchors.fill: parent
        anchors.margins: 18
        spacing: 18

        Rectangle {
            id: navPanel
            Layout.preferredWidth: 118
            Layout.fillHeight: true
            radius: 28
            border.color: root.glassBorder
            border.width: 1
            gradient: Gradient {
                GradientStop { position: 0.0; color: "#26FFFFFF" }
                GradientStop { position: 1.0; color: "#0EFFFFFF" }
            }

            Rectangle {
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: parent.top
                height: 88
                radius: 28
                gradient: Gradient {
                    GradientStop { position: 0.0; color: "#2CFFFFFF" }
                    GradientStop { position: 1.0; color: "#00FFFFFF" }
                }
            }

            Column {
                anchors.fill: parent
                anchors.leftMargin: 12
                anchors.rightMargin: 12
                anchors.topMargin: 14
                anchors.bottomMargin: 14
                spacing: 10

                Rectangle {
                    width: 66
                    height: 66
                    radius: 18
                    anchors.horizontalCenter: parent.horizontalCenter
                    border.color: "#4CC5D5E3"
                    border.width: 1
                    gradient: Gradient {
                        GradientStop { position: 0.0; color: "#34FFFFFF" }
                        GradientStop { position: 1.0; color: "#115FD3FF" }
                    }

                    Label {
                        anchors.centerIn: parent
                        text: "SM"
                        color: root.textPrimary
                        font.pixelSize: 22
                        font.bold: true
                    }
                }

                Item {
                    width: 1
                    height: 6
                }

                Repeater {
                    model: [
                        { key: "home", icon: "HM", name: "Home" },
                        { key: "queue", icon: "QU", name: "Queue" },
                        { key: "library", icon: "LB", name: "Library" },
                        { key: "settings", icon: "ST", name: "Settings" }
                    ]

                    delegate: Button {
                        property bool active: root.currentPage === modelData.key

                        width: 94
                        height: 76
                        anchors.horizontalCenter: parent.horizontalCenter
                        flat: true
                        onClicked: root.openPage(modelData.key)

                        background: Rectangle {
                            radius: 20
                            border.width: active ? 1 : 0
                            border.color: active ? "#70D7E6F2" : "transparent"
                            gradient: Gradient {
                                GradientStop { position: 0.0; color: active ? "#365FD3FF" : "#08FFFFFF" }
                                GradientStop { position: 1.0; color: active ? "#184FA4D0" : "#04FFFFFF" }
                            }
                        }

                        contentItem: Column {
                            spacing: 2
                            anchors.centerIn: parent

                            Label {
                                text: modelData.icon
                                color: active ? "#F7FCFF" : "#96ABBB"
                                horizontalAlignment: Text.AlignHCenter
                                font.bold: true
                                font.pixelSize: 16
                            }
                            Label {
                                text: modelData.name
                                color: active ? "#EAF7FF" : "#9CB0BF"
                                horizontalAlignment: Text.AlignHCenter
                                font.pixelSize: 12
                            }
                        }
                    }
                }

                Item {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 70
                    height: 1
                    Rectangle {
                        anchors.fill: parent
                        color: "#26FFFFFF"
                    }
                }

                Label {
                    anchors.horizontalCenter: parent.horizontalCenter
                    text: "SDK17"
                    color: "#8FA6B7"
                    font.pixelSize: 11
                    font.letterSpacing: 1.2
                }
            }
        }

        Rectangle {
            id: mainPanel
            Layout.fillWidth: true
            Layout.fillHeight: true
            radius: 30
            border.color: root.glassBorder
            border.width: 1
            gradient: Gradient {
                GradientStop { position: 0.0; color: root.glassPanelTop }
                GradientStop { position: 1.0; color: root.glassPanelBottom }
            }

            StackLayout {
                anchors.fill: parent
                anchors.margins: 16
                currentIndex: root.currentPage === "home" ? 0 :
                              root.currentPage === "queue" ? 1 :
                              root.currentPage === "library" ? 2 : 3

                Item {
                    RowLayout {
                        anchors.fill: parent
                        spacing: 16

                        Rectangle {
                            id: playerCard
                            Layout.fillHeight: true
                            Layout.fillWidth: true
                            radius: 24
                            border.color: "#4CC3D7E6"
                            border.width: 1
                            gradient: Gradient {
                                GradientStop { position: 0.0; color: "#28FFFFFF" }
                                GradientStop { position: 1.0; color: "#102C3947" }
                            }

                            Rectangle {
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.top: parent.top
                                height: 110
                                radius: 24
                                gradient: Gradient {
                                    GradientStop { position: 0.0; color: "#33FFFFFF" }
                                    GradientStop { position: 1.0; color: "#00FFFFFF" }
                                }
                            }

                            ColumnLayout {
                                anchors.fill: parent
                                anchors.leftMargin: 24
                                anchors.rightMargin: 24
                                anchors.topMargin: 22
                                anchors.bottomMargin: 20
                                spacing: 18

                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 8

                                    Label {
                                        text: root.currentTrack.title
                                        color: root.textPrimary
                                        font.pixelSize: 42
                                        font.bold: true
                                        elide: Text.ElideRight
                                        Layout.fillWidth: true
                                    }

                                    Label {
                                        text: root.currentTrack.artist
                                        color: root.textSecondary
                                        font.pixelSize: 24
                                        elide: Text.ElideRight
                                        Layout.fillWidth: true
                                    }
                                }

                                Rectangle {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 108
                                    radius: 18
                                    border.color: "#3E8E9FAF"
                                    border.width: 1
                                    color: "#18313F4D"

                                    ColumnLayout {
                                        anchors.fill: parent
                                        anchors.leftMargin: 18
                                        anchors.rightMargin: 18
                                        anchors.topMargin: 16
                                        anchors.bottomMargin: 12
                                        spacing: 8

                                        Slider {
                                            id: progressSlider
                                            Layout.fillWidth: true
                                            from: 0
                                            to: 1
                                            value: root.playbackPosition
                                            onMoved: root.seekNormalized(value)

                                            background: Rectangle {
                                                x: progressSlider.leftPadding
                                                y: progressSlider.topPadding + progressSlider.availableHeight / 2 - height / 2
                                                width: progressSlider.availableWidth
                                                height: 8
                                                radius: 4
                                                color: "#2B3F4F5E"
                                                border.color: "#3F8FA6B8"
                                                border.width: 1

                                                Rectangle {
                                                    width: progressSlider.visualPosition * parent.width
                                                    height: parent.height
                                                    radius: 4
                                                    color: root.accent
                                                }
                                            }

                                            handle: Rectangle {
                                                x: progressSlider.leftPadding + progressSlider.visualPosition * (progressSlider.availableWidth - width)
                                                y: progressSlider.topPadding + progressSlider.availableHeight / 2 - height / 2
                                                width: 18
                                                height: 18
                                                radius: 9
                                                color: "#F4FAFF"
                                                border.color: "#79CDEEFF"
                                                border.width: 1
                                            }
                                        }
                                    }
                                }

                                RowLayout {
                                    Layout.alignment: Qt.AlignHCenter
                                    spacing: 18

                                    Button {
                                        id: prevButton
                                        width: 72
                                        height: 72
                                        flat: true
                                        onClicked: {
                                            if (typeof bridge !== "undefined" && bridge.previousTrack) {
                                                bridge.previousTrack()
                                            }
                                        }
                                        background: Rectangle {
                                            radius: 22
                                            border.color: "#5D9AB1C0"
                                            border.width: 1
                                            color: root.buttonDark
                                        }
                                        contentItem: Label {
                                            text: "\u23EE"
                                            color: root.textPrimary
                                            horizontalAlignment: Text.AlignHCenter
                                            verticalAlignment: Text.AlignVCenter
                                            font.pixelSize: 24
                                            font.bold: true
                                        }
                                    }

                                    Button {
                                        id: playButton
                                        width: 92
                                        height: 92
                                        flat: true
                                        onClicked: root.isPlaying = !root.isPlaying
                                        background: Rectangle {
                                            radius: 30
                                            border.color: "#7CD8EEFF"
                                            border.width: 1
                                            gradient: Gradient {
                                                GradientStop { position: 0.0; color: root.accent }
                                                GradientStop { position: 1.0; color: root.accentSoft }
                                            }
                                        }
                                        contentItem: Label {
                                            text: root.isPlaying ? "\u23F8" : "\u25B6"
                                            color: "#F8FDFF"
                                            horizontalAlignment: Text.AlignHCenter
                                            verticalAlignment: Text.AlignVCenter
                                            font.pixelSize: 30
                                            font.bold: true
                                        }
                                    }

                                    Button {
                                        id: nextButton
                                        width: 72
                                        height: 72
                                        flat: true
                                        onClicked: {
                                            if (typeof bridge !== "undefined" && bridge.nextTrack) {
                                                bridge.nextTrack()
                                            }
                                        }
                                        background: Rectangle {
                                            radius: 22
                                            border.color: "#5D9AB1C0"
                                            border.width: 1
                                            color: root.buttonDark
                                        }
                                        contentItem: Label {
                                            text: "\u23ED"
                                            color: root.textPrimary
                                            horizontalAlignment: Text.AlignHCenter
                                            verticalAlignment: Text.AlignVCenter
                                            font.pixelSize: 24
                                            font.bold: true
                                        }
                                    }
                                }
                            }
                        }

                        Rectangle {
                            id: sidePanel
                            Layout.fillHeight: true
                            Layout.preferredWidth: 352
                            radius: 24
                            border.color: "#4BA3B8C7"
                            border.width: 1
                            gradient: Gradient {
                                GradientStop { position: 0.0; color: "#24FFFFFF" }
                                GradientStop { position: 1.0; color: "#10263544" }
                            }

                            ColumnLayout {
                                anchors.fill: parent
                                anchors.margins: 12
                                spacing: 12

                                TabBar {
                                    id: homeTabBar
                                    Layout.fillWidth: true
                                    background: Rectangle {
                                        radius: 16
                                        color: "#1A2A3644"
                                        border.color: "#3E92A5B5"
                                        border.width: 1
                                    }

                                    TabButton {
                                        text: "Lyrics"
                                        contentItem: Label {
                                            text: parent.text
                                            color: parent.checked ? "#F6FCFF" : "#99AEBE"
                                            horizontalAlignment: Text.AlignHCenter
                                            verticalAlignment: Text.AlignVCenter
                                            font.pixelSize: 15
                                            font.bold: parent.checked
                                        }
                                        background: Rectangle {
                                            radius: 12
                                            color: parent.checked ? "#3A5A6B7A" : "transparent"
                                        }
                                    }

                                    TabButton {
                                        text: "Recs"
                                        contentItem: Label {
                                            text: parent.text
                                            color: parent.checked ? "#F6FCFF" : "#99AEBE"
                                            horizontalAlignment: Text.AlignHCenter
                                            verticalAlignment: Text.AlignVCenter
                                            font.pixelSize: 15
                                            font.bold: parent.checked
                                        }
                                        background: Rectangle {
                                            radius: 12
                                            color: parent.checked ? "#3A5A6B7A" : "transparent"
                                        }
                                    }
                                }

                                StackLayout {
                                    Layout.fillWidth: true
                                    Layout.fillHeight: true
                                    currentIndex: homeTabBar.currentIndex

                                    ListView {
                                        id: lyricsList
                                        clip: true
                                        model: root.lyricLines
                                        currentIndex: root.currentLyricIndex

                                        ScrollBar.vertical: ScrollBar {
                                            width: 8
                                            background: Rectangle {
                                                radius: 4
                                                color: "#1E304050"
                                            }
                                            contentItem: Rectangle {
                                                radius: 4
                                                color: "#5FA8C5D8"
                                            }
                                        }

                                        delegate: Item {
                                            width: ListView.view.width
                                            height: lyricLine.implicitHeight + 10

                                            Label {
                                                id: lyricLine
                                                anchors.left: parent.left
                                                anchors.right: parent.right
                                                anchors.leftMargin: 8
                                                anchors.rightMargin: 8
                                                anchors.verticalCenter: parent.verticalCenter
                                                text: modelData.text
                                                wrapMode: Text.WordWrap
                                                color: index === root.currentLyricIndex ? "#F3FBFF" : "#AFC2D1"
                                                font.pixelSize: index === root.currentLyricIndex ? 18 : 17
                                                font.bold: index === root.currentLyricIndex
                                            }
                                        }
                                    }

                                    ListView {
                                        clip: true
                                        model: root.recommendations

                                        ScrollBar.vertical: ScrollBar {
                                            width: 8
                                            background: Rectangle {
                                                radius: 4
                                                color: "#1E304050"
                                            }
                                            contentItem: Rectangle {
                                                radius: 4
                                                color: "#5FA8C5D8"
                                            }
                                        }

                                        delegate: ItemDelegate {
                                            width: ListView.view.width
                                            height: 70
                                            highlighted: pressed || hovered
                                            onClicked: {
                                                root.currentTrack = {
                                                    title: modelData.title,
                                                    artist: modelData.artist
                                                }
                                                root.seekNormalized(0)
                                                root.isPlaying = true
                                            }

                                            background: Rectangle {
                                                radius: 14
                                                color: highlighted ? "#365C7382" : "#1220303D"
                                                border.color: highlighted ? "#6CC6E5F8" : "#31495B69"
                                                border.width: 1
                                            }

                                            contentItem: Column {
                                                anchors.fill: parent
                                                anchors.leftMargin: 14
                                                anchors.rightMargin: 14
                                                anchors.topMargin: 10
                                                spacing: 2

                                                Label {
                                                    text: modelData.title
                                                    color: "#EAF5FD"
                                                    font.pixelSize: 17
                                                    font.bold: true
                                                    elide: Text.ElideRight
                                                }
                                                Label {
                                                    text: modelData.artist
                                                    color: "#9FB6C7"
                                                    font.pixelSize: 13
                                                    elide: Text.ElideRight
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Pane {
                    background: null
                    Rectangle {
                        anchors.fill: parent
                        radius: 24
                        border.color: "#417E96AA"
                        border.width: 1
                        color: "#162A3744"
                        Label {
                            anchors.centerIn: parent
                            text: "Queue Page"
                            color: root.textPrimary
                            font.pixelSize: 30
                            font.bold: true
                        }
                    }
                }

                Pane {
                    background: null
                    Rectangle {
                        anchors.fill: parent
                        radius: 24
                        border.color: "#417E96AA"
                        border.width: 1
                        color: "#162A3744"
                        Label {
                            anchors.centerIn: parent
                            text: "Library Page"
                            color: root.textPrimary
                            font.pixelSize: 30
                            font.bold: true
                        }
                    }
                }

                Pane {
                    background: null
                    Rectangle {
                        anchors.fill: parent
                        radius: 24
                        border.color: "#417E96AA"
                        border.width: 1
                        color: "#162A3744"

                        ColumnLayout {
                            anchors.centerIn: parent
                            spacing: 14

                            Label {
                                text: "Settings Page"
                                color: root.textPrimary
                                font.pixelSize: 30
                                font.bold: true
                                Layout.alignment: Qt.AlignHCenter
                            }

                            RowLayout {
                                spacing: 10
                                Layout.alignment: Qt.AlignHCenter

                                Button {
                                    text: "Test"
                                    onClicked: {
                                        if (typeof bridge !== "undefined" && bridge.runSettingsTests) {
                                            bridge.runSettingsTests()
                                        }
                                    }
                                }

                                Button {
                                    text: "Save"
                                    onClicked: {
                                        if (typeof bridge !== "undefined" && bridge.saveSettings) {
                                            bridge.saveSettings()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
