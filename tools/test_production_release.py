import unittest
from production_release import production_version, validate_request, verify_certificate

class ProductionReleaseTests(unittest.TestCase):
    def test_forward_versions_increase_across_rollback(self):
        self.assertGreater(production_version(2), production_version(1))
        for number in (0, -1, 100000001):
            with self.assertRaises(ValueError): production_version(number)
    def test_requires_full_commit_and_reviewed_rollback(self):
        validate_request('a' * 40, 'release', False)
        validate_request('a' * 40, 'rollback', True)
        for source, mode, reviewed in [('main','release',False), ('a'*40,'rollback',False), ('a'*40,'oops',True)]:
            with self.assertRaises(ValueError): validate_request(source,mode,reviewed)
    def test_rejects_preview_wrong_and_multiple_signers(self):
        preview = 'a'*64; production = 'b'*64
        verify_certificate('Signer #1 certificate SHA-256 digest: '+production, production, preview)
        for report, expected in [('Signer #1 certificate SHA-256 digest: '+preview,preview),
                                 ('Signer #1 certificate SHA-256 digest: '+preview,production),
                                 ('Signer #1 certificate SHA-256 digest: '+production+'\nSigner #2 certificate SHA-256 digest: '+production,production)]:
            with self.assertRaises(ValueError): verify_certificate(report, expected, preview)

if __name__ == '__main__': unittest.main()
