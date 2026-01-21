# F-Droid Submission Guide for RepoStore

This folder contains the metadata file for submitting RepoStore to the official F-Droid repository.

## Files

- `com.samyak.repostore.yml` - F-Droid build metadata

## Submission Steps

### 1. Fork fdroiddata Repository
```bash
# Go to https://gitlab.com/fdroid/fdroiddata and click "Fork"
```

### 2. Clone Your Fork
```bash
git clone https://gitlab.com/<your-username>/fdroiddata.git
cd fdroiddata
```

### 3. Copy Metadata File
```bash
# Copy the metadata file to fdroiddata
cp /path/to/RepoStore/fdroid/com.samyak.repostore.yml metadata/
```

### 4. Validate (Optional but Recommended)
```bash
# Install fdroidserver
pip install fdroidserver

# Lint your metadata
fdroid lint com.samyak.repostore

# Try to rewrite/format
fdroid rewritemeta com.samyak.repostore
```

### 5. Create a Git Tag for the Release
Make sure you have a tagged release in your GitHub repo:
```bash
cd /path/to/RepoStore
git tag -a v1.0.8 -m "Release 1.0.8"
git push origin v1.0.8
```

### 6. Commit and Push to Your Fork
```bash
cd fdroiddata
git checkout -b add-repostore
git add metadata/com.samyak.repostore.yml
git commit -m "New app: RepoStore - GitHub App Store Alternative"
git push origin add-repostore
```

### 7. Create Merge Request
1. Go to your fork on GitLab
2. Click "Create merge request"
3. Target: `fdroid/fdroiddata` → `master`
4. Fill in the description
5. Submit!

## Important Notes

- F-Droid will build the app from source using the commit/tag specified
- The signing key fingerprint needs to be added after the first successful build
- Make sure all dependencies can be built from source
- No proprietary libraries allowed (Google Play Services, Firebase, etc.)

## Links

- [F-Droid Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/)
- [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/)
- [Submitting to F-Droid Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
