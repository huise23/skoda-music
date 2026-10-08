#ifndef SKODA_MUSIC_BSPATCH_H
#define SKODA_MUSIC_BSPATCH_H

#ifdef __cplusplus
extern "C" {
#endif

#define BSPATCH_SUCCESS 0
#define BSPATCH_ERR_OPEN_PATCH -1
#define BSPATCH_ERR_OPEN_OLD -2
#define BSPATCH_ERR_OPEN_NEW -3
#define BSPATCH_ERR_READ_HEADER -4
#define BSPATCH_ERR_CORRUPT_MAGIC -5
#define BSPATCH_ERR_INVALID_HEADER -6
#define BSPATCH_ERR_MALLOC -7
#define BSPATCH_ERR_READ_OLD -8
#define BSPATCH_ERR_BZ_INIT -9
#define BSPATCH_ERR_CORRUPT_PATCH -10
#define BSPATCH_ERR_WRITE_NEW -11

/**
 * Applies a bsdiff patch to oldfile and produces newfile.
 * @param oldfile Path to the existing base file (e.g. installed APK).
 * @param newfile Path to the destination file.
 * @param patchfile Path to the bsdiff patch file.
 * @return 0 on success, negative error code on failure.
 */
int bspatch_apply(const char *oldfile, const char *newfile, const char *patchfile);

#ifdef __cplusplus
}
#endif

#endif  // SKODA_MUSIC_BSPATCH_H
