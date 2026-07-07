# Releasing mnemo-cache to Maven Central

Publishing goes through the [Central Portal](https://central.sonatype.com) (the current
process; legacy OSSRH is retired for new accounts). The POM is already wired: the `release`
profile builds the sources + javadoc jars, GPG-signs everything, and uploads via the
`central-publishing-maven-plugin`.

> ⚠️ **Publishing is permanent.** A released version on Maven Central can never be deleted or
> overwritten — only superseded by a new version. Review carefully before you publish.

## One-time setup

1. **Central Portal account** — sign up at https://central.sonatype.com.
2. **Verify the namespace** `io.github.baokhang83` — the portal proves your GitHub ownership
   (follow its verification prompt). Until verified, uploads are rejected.
3. **GPG key** — generate one and publish the public key to a keyserver:
   ```bash
   gpg --gen-key
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```
4. **Credentials in `~/.m2/settings.xml`** — a token from the portal (Account → Generate
   User Token), plus the GPG passphrase:
   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username><!-- portal token username --></username>
         <password><!-- portal token password --></password>
       </server>
     </servers>
     <profiles>
       <profile>
         <id>release</id>
         <properties>
           <gpg.passphrase><!-- your GPG passphrase --></gpg.passphrase>
         </properties>
       </profile>
     </profiles>
   </settings>
   ```

## Cutting a release

1. Set the version (drop `-SNAPSHOT`):
   ```bash
   mvn versions:set -DnewVersion=0.1.0
   ```
2. Build, sign, and upload:
   ```bash
   mvn -Prelease deploy
   ```
   With `<autoPublish>false</autoPublish>` (current setting) this uploads a *deployment* to
   the portal but does **not** publish it. Go to the portal, inspect the validation results,
   and click **Publish** when satisfied. (Flip `autoPublish` to `true` later to skip the
   manual step.)
3. Tag and bump to the next snapshot:
   ```bash
   git tag v0.1.0
   mvn versions:set -DnewVersion=0.2.0-SNAPSHOT
   ```

## On version numbers

Publish `0.x` until the API has proven itself — `1.0.0` is a semver promise of API stability.
Reserve it for once the surface has settled and had real-world use.
