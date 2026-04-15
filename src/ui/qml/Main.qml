import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ApplicationWindow {
    id: root
    visible: true
    width: 1280
    height: 720
    title: "Skoda Music MVP"
    color: "#101317"

    property string currentPage: "home"

    function openPage(pageName) {
        currentPage = pageName
        if (typeof bridge !== "undefined" && bridge.navigateToPageName) {
            bridge.navigateToPageName(pageName)
        }
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        Rectangle {
            Layout.preferredWidth: 220
            Layout.fillHeight: true
            color: "#171C21"

            Column {
                anchors.fill: parent
                anchors.margins: 20
                spacing: 12

                Label {
                    text: "Skoda Music"
                    color: "#E6E6E6"
                    font.pixelSize: 26
                }

                Repeater {
                    model: [
                        { key: "home", title: "Home" },
                        { key: "library", title: "Library" },
                        { key: "queue", title: "Queue" },
                        { key: "settings", title: "Settings" }
                    ]

                    delegate: Button {
                        text: modelData.title
                        width: 180
                        highlighted: root.currentPage === modelData.key
                        onClicked: root.openPage(modelData.key)
                    }
                }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: "#101317"

            StackLayout {
                anchors.fill: parent
                anchors.margins: 20
                currentIndex: root.currentPage === "home" ? 0 :
                              root.currentPage === "library" ? 1 :
                              root.currentPage === "queue" ? 2 : 3

                Pane {
                    Column {
                        spacing: 10
                        Label { text: "Home"; font.pixelSize: 28; color: "#F2F2F2" }
                        Label { text: "Current Playing + Recommendations"; color: "#B8C0CA" }
                        Row {
                            spacing: 8
                            Button {
                                text: "Prev"
                                onClicked: if (typeof bridge !== "undefined" && bridge.previousTrack) bridge.previousTrack()
                            }
                            Button {
                                text: "Next"
                                onClicked: if (typeof bridge !== "undefined" && bridge.nextTrack) bridge.nextTrack()
                            }
                            Button {
                                text: "Lyrics"
                                onClicked: if (typeof bridge !== "undefined" && bridge.requestLyricsForCurrentTrack) bridge.requestLyricsForCurrentTrack()
                            }
                        }
                    }
                }

                Pane {
                    Column {
                        spacing: 10
                        Label { text: "Library"; font.pixelSize: 28; color: "#F2F2F2" }
                        Label { text: "Search and server-first list placeholder"; color: "#B8C0CA" }
                    }
                }

                Pane {
                    Column {
                        spacing: 10
                        Label { text: "Queue"; font.pixelSize: 28; color: "#F2F2F2" }
                        Label { text: "Current queue placeholder, sync from bridge snapshot"; color: "#B8C0CA" }
                    }
                }

                Pane {
                    Column {
                        spacing: 10
                        Label { text: "Settings"; font.pixelSize: 28; color: "#F2F2F2" }
                        Label { text: "Emby and LrcApi forms placeholder"; color: "#B8C0CA" }
                        Row {
                            spacing: 8
                            Button {
                                text: "Test"
                                onClicked: if (typeof bridge !== "undefined" && bridge.runSettingsTests) bridge.runSettingsTests()
                            }
                            Button {
                                text: "Save"
                                onClicked: if (typeof bridge !== "undefined" && bridge.saveSettings) bridge.saveSettings()
                            }
                        }
                    }
                }
            }
        }
    }
}
