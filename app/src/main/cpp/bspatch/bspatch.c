#include "bspatch.h"

#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "bzlib.h"

#define HEADER_SIZE 32

static int64_t offtin(const uint8_t *buf) {
  int64_t y;
  y = buf[7] & 0x7F;
  y = y * 256 + buf[6];
  y = y * 256 + buf[5];
  y = y * 256 + buf[4];
  y = y * 256 + buf[3];
  y = y * 256 + buf[2];
  y = y * 256 + buf[1];
  y = y * 256 + buf[0];
  if (buf[7] & 0x80) {
    y = -y;
  }
  return y;
}

int bspatch_apply(const char *oldfile, const char *newfile, const char *patchfile) {
  FILE *f = NULL;
  FILE *cpf = NULL;
  FILE *dpf = NULL;
  FILE *epf = NULL;
  BZFILE *cpfbz2 = NULL;
  BZFILE *dpfbz2 = NULL;
  BZFILE *epfbz2 = NULL;
  int cbz2err = 0;
  int dbz2err = 0;
  int ebz2err = 0;
  FILE *oldf = NULL;
  FILE *newf = NULL;
  uint8_t header[HEADER_SIZE];
  uint8_t buf[8];
  uint8_t *old_buf = NULL;
  uint8_t *new_buf = NULL;
  int result = BSPATCH_SUCCESS;

  if (oldfile == NULL || newfile == NULL || patchfile == NULL) {
    return BSPATCH_ERR_OPEN_PATCH;
  }

  f = fopen(patchfile, "rb");
  if (f == NULL) {
    return BSPATCH_ERR_OPEN_PATCH;
  }

  if (fread(header, 1, HEADER_SIZE, f) < HEADER_SIZE) {
    fclose(f);
    return BSPATCH_ERR_READ_HEADER;
  }

  if (memcmp(header, "BSDIFF40", 8) != 0) {
    fclose(f);
    return BSPATCH_ERR_CORRUPT_MAGIC;
  }

  int64_t bzctrllen = offtin(header + 8);
  int64_t bzdatalen = offtin(header + 16);
  int64_t newsize = offtin(header + 24);

  if (bzctrllen < 0 || bzdatalen < 0 || newsize < 0 ||
      bzctrllen > (INT64_MAX - HEADER_SIZE) ||
      bzdatalen > (INT64_MAX - HEADER_SIZE - bzctrllen) ||
      newsize > 2147483647LL) {  // Limit to 2GB max for safe memory allocation
    fclose(f);
    return BSPATCH_ERR_INVALID_HEADER;
  }
  fclose(f);

  cpf = fopen(patchfile, "rb");
  dpf = fopen(patchfile, "rb");
  epf = fopen(patchfile, "rb");
  if (cpf == NULL || dpf == NULL || epf == NULL) {
    result = BSPATCH_ERR_OPEN_PATCH;
    goto cleanup;
  }

  int64_t offset = HEADER_SIZE;
  if (fseeko(cpf, (off_t)offset, SEEK_SET) != 0) {
    result = BSPATCH_ERR_BZ_INIT;
    goto cleanup;
  }
  cpfbz2 = BZ2_bzReadOpen(&cbz2err, cpf, 0, 0, NULL, 0);
  if (cpfbz2 == NULL || cbz2err != BZ_OK) {
    result = BSPATCH_ERR_BZ_INIT;
    goto cleanup;
  }

  offset += bzctrllen;
  if (fseeko(dpf, (off_t)offset, SEEK_SET) != 0) {
    result = BSPATCH_ERR_BZ_INIT;
    goto cleanup;
  }
  dpfbz2 = BZ2_bzReadOpen(&dbz2err, dpf, 0, 0, NULL, 0);
  if (dpfbz2 == NULL || dbz2err != BZ_OK) {
    result = BSPATCH_ERR_BZ_INIT;
    goto cleanup;
  }

  offset += bzdatalen;
  if (fseeko(epf, (off_t)offset, SEEK_SET) != 0) {
    result = BSPATCH_ERR_BZ_INIT;
    goto cleanup;
  }
  epfbz2 = BZ2_bzReadOpen(&ebz2err, epf, 0, 0, NULL, 0);
  if (epfbz2 == NULL || ebz2err != BZ_OK) {
    result = BSPATCH_ERR_BZ_INIT;
    goto cleanup;
  }

  oldf = fopen(oldfile, "rb");
  if (oldf == NULL) {
    result = BSPATCH_ERR_OPEN_OLD;
    goto cleanup;
  }
  if (fseeko(oldf, 0, SEEK_END) != 0) {
    result = BSPATCH_ERR_READ_OLD;
    goto cleanup;
  }
  int64_t oldsize = ftello(oldf);
  if (oldsize < 0 || fseeko(oldf, 0, SEEK_SET) != 0) {
    result = BSPATCH_ERR_READ_OLD;
    goto cleanup;
  }

  old_buf = (uint8_t *)malloc((size_t)oldsize + 1);
  if (old_buf == NULL && oldsize > 0) {
    result = BSPATCH_ERR_MALLOC;
    goto cleanup;
  }
  if (oldsize > 0 && fread(old_buf, 1, (size_t)oldsize, oldf) != (size_t)oldsize) {
    result = BSPATCH_ERR_READ_OLD;
    goto cleanup;
  }
  fclose(oldf);
  oldf = NULL;

  new_buf = (uint8_t *)malloc((size_t)newsize + 1);
  if (new_buf == NULL && newsize > 0) {
    result = BSPATCH_ERR_MALLOC;
    goto cleanup;
  }

  int64_t oldpos = 0;
  int64_t newpos = 0;
  int64_t ctrl[3];

  while (newpos < newsize) {
    for (int i = 0; i <= 2; ++i) {
      int lenread = BZ2_bzRead(&cbz2err, cpfbz2, buf, 8);
      if (lenread < 8 || (cbz2err != BZ_OK && cbz2err != BZ_STREAM_END)) {
        result = BSPATCH_ERR_CORRUPT_PATCH;
        goto cleanup;
      }
      ctrl[i] = offtin(buf);
    }

    if (ctrl[0] < 0 || ctrl[1] < 0 ||
        (newpos + ctrl[0]) > newsize ||
        (newpos + ctrl[0] + ctrl[1]) > newsize) {
      result = BSPATCH_ERR_CORRUPT_PATCH;
      goto cleanup;
    }

    int lenread = BZ2_bzRead(&dbz2err, dpfbz2, new_buf + newpos, (int)ctrl[0]);
    if (lenread < (int)ctrl[0] || (dbz2err != BZ_OK && dbz2err != BZ_STREAM_END)) {
      result = BSPATCH_ERR_CORRUPT_PATCH;
      goto cleanup;
    }

    for (int64_t i = 0; i < ctrl[0]; ++i) {
      if ((oldpos + i) >= 0 && (oldpos + i) < oldsize) {
        new_buf[newpos + i] = (uint8_t)(new_buf[newpos + i] + old_buf[oldpos + i]);
      }
    }

    newpos += ctrl[0];
    oldpos += ctrl[0];

    lenread = BZ2_bzRead(&ebz2err, epfbz2, new_buf + newpos, (int)ctrl[1]);
    if (lenread < (int)ctrl[1] || (ebz2err != BZ_OK && ebz2err != BZ_STREAM_END)) {
      result = BSPATCH_ERR_CORRUPT_PATCH;
      goto cleanup;
    }

    newpos += ctrl[1];
    oldpos += ctrl[2];
  }

  newf = fopen(newfile, "wb");
  if (newf == NULL) {
    result = BSPATCH_ERR_OPEN_NEW;
    goto cleanup;
  }
  if (newsize > 0 && fwrite(new_buf, 1, (size_t)newsize, newf) != (size_t)newsize) {
    result = BSPATCH_ERR_WRITE_NEW;
    goto cleanup;
  }
  fclose(newf);
  newf = NULL;

cleanup:
  if (cpfbz2 != NULL) BZ2_bzReadClose(&cbz2err, cpfbz2);
  if (dpfbz2 != NULL) BZ2_bzReadClose(&dbz2err, dpfbz2);
  if (epfbz2 != NULL) BZ2_bzReadClose(&ebz2err, epfbz2);
  if (cpf != NULL) fclose(cpf);
  if (dpf != NULL) fclose(dpf);
  if (epf != NULL) fclose(epf);
  if (oldf != NULL) fclose(oldf);
  if (newf != NULL) {
    fclose(newf);
    unlink(newfile);
  }
  if (old_buf != NULL) free(old_buf);
  if (new_buf != NULL) free(new_buf);

  return result;
}
